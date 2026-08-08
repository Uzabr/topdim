package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.PartnerApplicationRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerApplicationServiceTest {

    @Mock private PartnerApplicationRepository repository;
    @Mock private CouponMerchantClient couponMerchantClient;
    @Mock private PartnerApplicationApprovalService approvalService;
    @Mock private AuditLogService auditLogService;

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

        when(repository.existsByPhoneAndStatusIn(
                "+998901234567", java.util.List.of(ApplicationStatus.PENDING, ApplicationStatus.PROCESSING)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active partner application already exists");

        verify(repository, never()).save(any(PartnerApplication.class));
    }

    @Test
    @DisplayName("submit: phone with an approval already in progress is rejected")
    void submit_processingPhone_rejected() {
        PartnerApplicationRequest request = new PartnerApplicationRequest();
        request.setFirstName("Ali");
        request.setLastName("Valiev");
        request.setPhone("+998 90 123 45 67");
        request.setCompanyName("Ali Cafe");

        when(repository.existsByPhoneAndStatusIn(
                "+998901234567", java.util.List.of(ApplicationStatus.PENDING, ApplicationStatus.PROCESSING)))
                .thenReturn(true);

        assertThatThrownBy(() -> service.submit(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active partner application already exists");
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
    @DisplayName("approve: prepared application creates merchant and completes the workflow")
    void approve_preparedApplication_createsMerchantAndCompletes() {
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

        MerchantOnboardingResponse merchant = new MerchantOnboardingResponse(77L, "Ali Cafe", 10L, true);

        when(approvalService.prepare(5L, request))
                .thenReturn(new PartnerApplicationApprovalService.Preparation(10L, null));
        when(couponMerchantClient.createMerchant(any(CreateMerchantOnboardingRequest.class)))
                .thenReturn(ApiResponse.success(merchant));
        app.setStatus(ApplicationStatus.APPROVED);
        app.setLinkedUserId(10L);
        app.setLinkedMerchantId(77L);
        when(approvalService.complete(5L, 99L, 77L)).thenReturn(app);

        PartnerApplicationResponse result = service.approve(5L, 99L, request);

        assertThat(result.getStatus()).isEqualTo(ApplicationStatus.APPROVED);
        assertThat(result.getLinkedUserId()).isEqualTo(10L);
        assertThat(result.getLinkedMerchantId()).isEqualTo(77L);
        verify(couponMerchantClient).createMerchant(argThat(req ->
                req.getUserId().equals(10L) && req.getName().equals("Ali Cafe")
        ));
        verify(approvalService).complete(5L, 99L, 77L);
    }

    @Test
    @DisplayName("approve: a completed retry returns the existing links without another merchant call")
    void approve_completedRetry_isIdempotent() {
        PartnerApplication app = PartnerApplication.builder()
                .id(5L).status(ApplicationStatus.APPROVED).source("WEB")
                .linkedUserId(10L).linkedMerchantId(77L).build();

        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        when(approvalService.prepare(5L, request))
                .thenReturn(new PartnerApplicationApprovalService.Preparation(10L, app));

        PartnerApplicationResponse result = service.approve(5L, 99L, request);

        assertThat(result.getLinkedMerchantId()).isEqualTo(77L);
        verify(couponMerchantClient, never()).createMerchant(any());
        verify(approvalService, never()).complete(any(), any(), any());
    }

    @Test
    @DisplayName("approve: missing application returns not found")
    void approve_missingApplication_throwsNotFound() {
        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        when(approvalService.prepare(404L, request))
                .thenThrow(new ResourceNotFoundException("Application not found: 404"));

        assertThatThrownBy(() -> service.approve(404L, 99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("approve: remote failure leaves the prepared application retryable")
    void approve_merchantFailure_doesNotComplete() {
        ApprovePartnerApplicationRequest request = new ApprovePartnerApplicationRequest();
        request.setMerchantName("Test");
        request.setAddress("addr");
        request.setPhone("+998901234567");
        when(approvalService.prepare(5L, request))
                .thenReturn(new PartnerApplicationApprovalService.Preparation(10L, null));
        when(couponMerchantClient.createMerchant(any()))
                .thenThrow(new IllegalStateException("coupon-service unavailable"));

        assertThatThrownBy(() -> service.approve(5L, 99L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unavailable");

        verify(approvalService, never()).complete(any(), any(), any());
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

        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));
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
        when(repository.findByIdForUpdate(5L)).thenReturn(Optional.of(app));

        RejectPartnerApplicationRequest request = new RejectPartnerApplicationRequest();
        request.setReason("test");

        assertThatThrownBy(() -> service.reject(5L, 99L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only PENDING");
    }

    @Test
    @DisplayName("reject: missing application returns not found")
    void reject_missingApplication_throwsNotFound() {
        when(repository.findByIdForUpdate(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reject(404L, 99L, new RejectPartnerApplicationRequest()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }
}
