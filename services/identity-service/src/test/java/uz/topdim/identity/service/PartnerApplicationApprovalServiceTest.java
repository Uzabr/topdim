package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.ApprovePartnerApplicationRequest;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.entity.PartnerApplication;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.PartnerApplicationRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerApplicationApprovalServiceTest {

    @Mock private PartnerApplicationRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private SecurityVersionService securityVersionService;

    @InjectMocks
    private PartnerApplicationApprovalService service;

    @Test
    @DisplayName("approval preparation and completion use independent short transactions")
    void approvalSteps_requireNewTransactions() throws Exception {
        Transactional prepare = PartnerApplicationApprovalService.class
                .getMethod("prepare", Long.class, ApprovePartnerApplicationRequest.class)
                .getAnnotation(Transactional.class);
        Transactional complete = PartnerApplicationApprovalService.class
                .getMethod("complete", Long.class, Long.class, Long.class)
                .getAnnotation(Transactional.class);

        assertThat(prepare.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(complete.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
        assertThat(PartnerApplicationService.class
                .getMethod("approve", Long.class, Long.class, ApprovePartnerApplicationRequest.class)
                .isAnnotationPresent(Transactional.class)).isFalse();
    }

    @Test
    @DisplayName("prepare: commits one linked user and marks a pending application as processing")
    void prepare_pendingApplication_linksUserAndMarksProcessing() {
        PartnerApplication app = application(ApplicationStatus.PENDING);
        ApprovePartnerApplicationRequest request = request();
        User persisted = partner(10L);

        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Temp12345")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(persisted);
        when(repository.save(any(PartnerApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.prepare(5L, request);

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.alreadyCompleted()).isFalse();
        assertThat(app.getStatus()).isEqualTo(ApplicationStatus.PROCESSING);
        assertThat(app.getLinkedUserId()).isEqualTo(10L);
        assertThat(app.getEmail()).isEqualTo("partner@example.uz");
        verify(repository).save(app);
    }

    @Test
    @DisplayName("prepare: retry reuses the linked user instead of creating another account")
    void prepare_processingApplication_reusesLinkedUser() {
        PartnerApplication app = application(ApplicationStatus.PROCESSING);
        app.setLinkedUserId(10L);
        app.setEmail("partner@example.uz");
        User existing = partner(10L);

        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(repository.save(any(PartnerApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovePartnerApplicationRequest retry = request();
        retry.setTemporaryPassword(null);

        var result = service.prepare(5L, retry);

        assertThat(result.userId()).isEqualTo(10L);
        assertThat(result.alreadyCompleted()).isFalse();
        verify(userRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("prepare: a new account still requires a temporary password")
    void prepare_newAccountWithoutPassword_rejected() {
        PartnerApplication app = application(ApplicationStatus.PENDING);
        ApprovePartnerApplicationRequest request = request();
        request.setTemporaryPassword(null);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(5L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Temporary password is required");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("prepare: retry with a different login is rejected")
    void prepare_processingApplicationWithDifferentLogin_rejected() {
        PartnerApplication app = application(ApplicationStatus.PROCESSING);
        app.setLinkedUserId(10L);
        app.setEmail("other@example.uz");

        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findById(10L)).thenReturn(Optional.of(partner(10L)));

        assertThatThrownBy(() -> service.prepare(5L, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different login or phone");
    }

    @Test
    @DisplayName("prepare: an already approved retry returns its completed application")
    void prepare_approvedApplication_isIdempotent() {
        PartnerApplication app = application(ApplicationStatus.APPROVED);
        app.setLinkedUserId(10L);
        app.setLinkedMerchantId(77L);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));

        var result = service.prepare(5L, request());

        assertThat(result.alreadyCompleted()).isTrue();
        assertThat(result.completedApplication()).isSameAs(app);
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("complete: only a processing application becomes approved")
    void complete_processingApplication_linksMerchantAndApproves() {
        PartnerApplication app = application(ApplicationStatus.PROCESSING);
        app.setLinkedUserId(10L);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(repository.save(any(PartnerApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PartnerApplication result = service.complete(5L, 99L, 77L);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(result.getLinkedMerchantId()).isEqualTo(77L);
        assertThat(result.getReviewedBy()).isEqualTo(99L);
    }

    @Test
    @DisplayName("prepare: admin or moderator account cannot be promoted to partner")
    void prepare_adminAccount_rejected() {
        PartnerApplication app = application(ApplicationStatus.PENDING);
        User admin = partner(10L);
        admin.setRole(Role.ADMIN);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.of(admin));
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.prepare(5L, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin or moderator");
    }

    @Test
    @DisplayName("prepare: email and phone belonging to different users are rejected")
    void prepare_phoneBelongsToDifferentUser_rejected() {
        PartnerApplication app = application(ApplicationStatus.PENDING);
        User emailUser = partner(10L);
        User phoneUser = partner(11L);
        phoneUser.setEmail("other@example.uz");
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.of(emailUser));
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(phoneUser));

        assertThatThrownBy(() -> service.prepare(5L, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Phone is already linked to another user");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("prepare: fills a missing phone on the matching email account")
    void prepare_emailAccountWithoutPhone_linksRequestedPhone() {
        PartnerApplication app = application(ApplicationStatus.PENDING);
        User existing = partner(10L);
        existing.setPhone(null);
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.of(existing));
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(userRepository.save(existing)).thenReturn(existing);
        when(repository.save(any(PartnerApplication.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.prepare(5L, request());

        assertThat(existing.getPhone()).isEqualTo("+998901234567");
        verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("prepare: refuses to overwrite a different phone on the email account")
    void prepare_emailAccountWithDifferentPhone_rejected() {
        PartnerApplication app = application(ApplicationStatus.PENDING);
        User existing = partner(10L);
        existing.setPhone("+998909999999");
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.of(existing));
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.prepare(5L, request()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different phone");

        verify(repository, never()).save(any());
    }

    private PartnerApplication application(ApplicationStatus status) {
        return PartnerApplication.builder()
                .id(5L)
                .firstName("Ali")
                .lastName("Valiyev")
                .phone("+998901234567")
                .companyName("Ali Cafe")
                .source("WEB")
                .status(status)
                .build();
    }

    private User partner(Long id) {
        return User.builder()
                .id(id)
                .email("partner@example.uz")
                .phone("+998901234567")
                .password("encoded")
                .firstName("Ali")
                .lastName("Valiyev")
                .role(Role.PARTNER)
                .enabled(true)
                .build();
    }

    private ApprovePartnerApplicationRequest request() {
        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        request.setLoginEmail("partner@example.uz");
        request.setTemporaryPassword("Temp12345");
        request.setMerchantName("Ali Cafe");
        request.setContactPerson("Ali Valiyev");
        request.setAddress("Amir Temur 10");
        request.setPhone("+998901234567");
        request.setCity("Tashkent");
        request.setWorkingHours("10:00-22:00");
        return request;
    }
}
