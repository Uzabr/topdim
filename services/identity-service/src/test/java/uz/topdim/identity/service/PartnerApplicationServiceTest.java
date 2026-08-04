package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.client.CouponMerchantClient;
import uz.topdim.identity.client.CreateMerchantOnboardingRequest;
import uz.topdim.identity.client.MerchantOnboardingResponse;
import uz.topdim.identity.dto.ApprovePartnerApplicationRequest;
import uz.topdim.identity.dto.PartnerApplicationRequest;
import uz.topdim.identity.dto.PartnerApplicationResponse;
import uz.topdim.identity.dto.RejectPartnerApplicationRequest;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.entity.PartnerApplication;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.PartnerApplicationRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerApplicationServiceTest {

    @Mock private PartnerApplicationRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CouponMerchantClient couponMerchantClient;
    @Mock private SecurityVersionService securityVersionService;

    @InjectMocks
    private PartnerApplicationService service;

    // ==================== Submit ====================

    @Test
    @DisplayName("submit: duplicate pending phone is rejected")
    void submit_duplicatePendingPhone_rejected() {
        PartnerApplicationRequest request = new PartnerApplicationRequest();
        request.setFirstName("Ali");
        request.setLastName("Valiev");
        request.setPhone("+998 90 123 45 67");
        request.setCompanyName("Ali Cafe");
        request.setCity("Tashkent");
        request.setAddress("Amir Temur 10");
        request.setBusinessCategory("Cafe");

        when(repository.existsByPhoneAndStatus("+998901234567", ApplicationStatus.PENDING)).thenReturn(true);

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pending partner application already exists");

        verify(repository, never()).save(any(PartnerApplication.class));
    }

    // ==================== Approve ====================

    @Test
    @DisplayName("getById: missing application returns not found")
    void getById_missingApplication_throwsNotFound() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("approve: pending application creates partner user and merchant")
    void approve_pendingApplication_createsPartnerUserAndMerchant() {
        PartnerApplication app = PartnerApplication.builder()
                .id(5L)
                .firstName("Ali")
                .lastName("Valiev")
                .phone("+998901234567")
                .companyName("Ali Cafe")
                .status(ApplicationStatus.PENDING)
                .source("WEB")
                .build();

        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        request.setLoginEmail("partner@example.uz");
        request.setTemporaryPassword("Temp12345");
        request.setMerchantName("Ali Cafe");
        request.setContactPerson("Ali Valiev");
        request.setAddress("Amir Temur 10");
        request.setPhone("+998901234567");
        request.setWorkingHours("10:00-22:00");

        User partner = User.builder()
                .id(10L)
                .email("partner@example.uz")
                .phone("+998901234567")
                .firstName("Ali")
                .lastName("Valiev")
                .role(Role.PARTNER)
                .enabled(true)
                .password("encoded")
                .build();

        MerchantOnboardingResponse merchant = new MerchantOnboardingResponse(77L, "Ali Cafe", 10L, true);

        when(repository.findById(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@example.uz")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("Temp12345")).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(partner);
        when(couponMerchantClient.createMerchant(any(CreateMerchantOnboardingRequest.class)))
                .thenReturn(ApiResponse.success(merchant));
        when(repository.save(any(PartnerApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        PartnerApplicationResponse result = service.approve(5L, 99L, request);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(result.getLinkedUserId()).isEqualTo(10L);
        assertThat(result.getLinkedMerchantId()).isEqualTo(77L);
        verify(couponMerchantClient).createMerchant(argThat(req ->
                req.getUserId().equals(10L) && req.getName().equals("Ali Cafe")
        ));
    }

    @Test
    @DisplayName("approve: non-pending application throws")
    void approve_nonPendingApplication_throws() {
        PartnerApplication app = PartnerApplication.builder()
                .id(5L).status(ApplicationStatus.APPROVED).source("WEB").build();
        when(repository.findById(5L)).thenReturn(Optional.of(app));

        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        request.setLoginEmail("p@test.uz");
        request.setTemporaryPassword("Temp12345");
        request.setMerchantName("Test");
        request.setAddress("addr");
        request.setPhone("+998901234567");

        assertThatThrownBy(() -> service.approve(5L, 99L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    @DisplayName("approve: missing application returns not found")
    void approve_missingApplication_throwsNotFound() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(404L, 99L, new ApprovePartnerApplicationRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("approve: admin/moderator user cannot be linked as partner")
    void approve_adminUser_throws() {
        PartnerApplication app = PartnerApplication.builder()
                .id(5L).firstName("Ali").lastName("V").phone("+998901234567")
                .companyName("X").status(ApplicationStatus.PENDING).source("WEB").build();

        User admin = User.builder().id(10L).email("admin@test.uz").role(Role.ADMIN)
                .password("pw").firstName("Ali").enabled(true).build();

        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        request.setLoginEmail("admin@test.uz");
        request.setTemporaryPassword("Temp12345");
        request.setMerchantName("Test");
        request.setAddress("addr");
        request.setPhone("+998901234567");

        when(repository.findById(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("admin@test.uz")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.approve(5L, 99L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Admin or moderator");
    }

    @Test
    @DisplayName("approve: phone linked to another user is rejected before merchant creation")
    void approve_phoneLinkedToAnotherUser_throws() {
        PartnerApplication app = PartnerApplication.builder()
                .id(5L).firstName("Ali").lastName("V").phone("+998901234567")
                .companyName("X").status(ApplicationStatus.PENDING).source("WEB").build();

        User emailUser = User.builder().id(10L).email("partner@test.uz").role(Role.USER)
                .password("pw").firstName("Ali").enabled(true).build();
        User phoneUser = User.builder().id(11L).email("other@test.uz").phone("+998901234567").role(Role.USER)
                .password("pw").firstName("Other").enabled(true).build();

        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        request.setLoginEmail("partner@test.uz");
        request.setTemporaryPassword("Temp12345");
        request.setMerchantName("Test");
        request.setAddress("addr");
        request.setPhone("+998901234567");

        when(repository.findById(5L)).thenReturn(Optional.of(app));
        when(userRepository.findByEmailIgnoreCase("partner@test.uz")).thenReturn(Optional.of(emailUser));
        when(userRepository.findByPhone("+998901234567")).thenReturn(Optional.of(phoneUser));

        assertThatThrownBy(() -> service.approve(5L, 99L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Phone is already linked to another user");

        verify(couponMerchantClient, never()).createMerchant(any(CreateMerchantOnboardingRequest.class));
        verify(repository, never()).save(any(PartnerApplication.class));
    }

    // ==================== Reject ====================

    @Test
    @DisplayName("reject: pending application with reason succeeds")
    void reject_pendingApplication_succeeds() {
        PartnerApplication app = PartnerApplication.builder()
                .id(5L).status(ApplicationStatus.PENDING).source("WEB")
                .firstName("Ali").lastName("V").phone("+998901234567").companyName("X").build();

        RejectPartnerApplicationRequest request = new RejectPartnerApplicationRequest();
        request.setReason("Incomplete documents");

        when(repository.findById(5L)).thenReturn(Optional.of(app));
        when(repository.save(any(PartnerApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        PartnerApplicationResponse result = service.reject(5L, 99L, request);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.REJECTED);
        assertThat(result.getRejectionReason()).isEqualTo("Incomplete documents");
    }

    @Test
    @DisplayName("reject: non-pending application throws")
    void reject_nonPendingApplication_throws() {
        PartnerApplication app = PartnerApplication.builder()
                .id(5L).status(ApplicationStatus.REJECTED).source("WEB").build();
        when(repository.findById(5L)).thenReturn(Optional.of(app));

        RejectPartnerApplicationRequest request = new RejectPartnerApplicationRequest();
        request.setReason("test");

        assertThatThrownBy(() -> service.reject(5L, 99L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    @DisplayName("reject: missing application returns not found")
    void reject_missingApplication_throwsNotFound() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(404L, 99L, new RejectPartnerApplicationRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }
}
