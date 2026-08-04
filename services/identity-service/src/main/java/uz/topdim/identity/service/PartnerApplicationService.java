package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnerApplicationService {

    private final PartnerApplicationRepository repository;
    private final CouponMerchantClient couponMerchantClient;
    private final PartnerApplicationApprovalService approvalService;

    // ==================== Submit ====================

    @Transactional
    public PartnerApplicationResponse submit(PartnerApplicationRequest request) {
        String phone = normalizePhone(request.getPhone());
        if (repository.existsByPhoneAndStatusIn(
                phone, List.of(ApplicationStatus.PENDING, ApplicationStatus.PROCESSING))) {
            throw new IllegalStateException("active partner application already exists for phone");
        }

        PartnerApplication app = PartnerApplication.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .phone(phone)
                .email(blankToNull(request.getEmail()))
                .companyName(request.getCompanyName().trim())
                .city(blankToNull(request.getCity()))
                .address(blankToNull(request.getAddress()))
                .workingHours(blankToNull(request.getWorkingHours()))
                .businessCategory(blankToNull(request.getBusinessCategory()))
                .website(blankToNull(request.getWebsite()))
                .telegramUsername(blankToNull(request.getTelegramUsername()))
                .comment(blankToNull(request.getComment()))
                .source("WEB")
                .status(ApplicationStatus.PENDING)
                .build();
        return toResponse(repository.save(app));
    }

    // ==================== Admin Read ====================

    public Page<PartnerApplicationResponse> getAll(ApplicationStatus status, Pageable pageable) {
        Page<PartnerApplication> page = status != null
                ? repository.findByStatus(status, pageable)
                : repository.findAll(pageable);
        return page.map(this::toResponse);
    }

    public PartnerApplicationResponse getById(Long id) {
        return toResponse(findApplication(id));
    }

    // ==================== Approve ====================

    public PartnerApplicationResponse approve(Long id, Long adminId, ApprovePartnerApplicationRequest request) {
        PartnerApplicationApprovalService.Preparation preparation = approvalService.prepare(id, request);
        if (preparation.alreadyCompleted()) {
            return toResponse(preparation.completedApplication());
        }

        CreateMerchantOnboardingRequest merchantRequest = CreateMerchantOnboardingRequest.builder()
                .userId(preparation.userId())
                .name(request.getMerchantName())
                .email(request.getLoginEmail())
                .website(request.getWebsite())
                .contactPerson(request.getContactPerson())
                .location(CreateMerchantOnboardingRequest.Location.builder()
                        .title(request.getCity())
                        .address(request.getAddress())
                        .phone(request.getPhone())
                        .workingHours(request.getWorkingHours())
                        .build())
                .build();

        ApiResponse<MerchantOnboardingResponse> merchantResponse = couponMerchantClient.createMerchant(merchantRequest);
        MerchantOnboardingResponse merchant = merchantResponse != null ? merchantResponse.getData() : null;
        if (merchant == null || merchant.getId() == null) {
            throw new IllegalStateException("Merchant onboarding failed");
        }

        return toResponse(approvalService.complete(id, adminId, merchant.getId()));
    }

    // ==================== Reject ====================

    @Transactional
    public PartnerApplicationResponse reject(Long id, Long adminId, RejectPartnerApplicationRequest request) {
        PartnerApplication app = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));

        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING application can be rejected");
        }

        app.setStatus(ApplicationStatus.REJECTED);
        app.setReviewedBy(adminId);
        app.setReviewedAt(LocalDateTime.now());
        app.setRejectionReason(request.getReason().trim());
        return toResponse(repository.save(app));
    }

    // ==================== Mappers ====================

    PartnerApplicationResponse toResponse(PartnerApplication e) {
        return PartnerApplicationResponse.builder()
                .id(e.getId())
                .firstName(e.getFirstName())
                .lastName(e.getLastName())
                .phone(e.getPhone())
                .email(e.getEmail())
                .companyName(e.getCompanyName())
                .city(e.getCity())
                .address(e.getAddress())
                .workingHours(e.getWorkingHours())
                .businessCategory(e.getBusinessCategory())
                .website(e.getWebsite())
                .telegramUsername(e.getTelegramUsername())
                .comment(e.getComment())
                .source(e.getSource())
                .status(e.getStatus())
                .rejectionReason(e.getRejectionReason())
                .reviewedBy(e.getReviewedBy())
                .reviewedAt(e.getReviewedAt())
                .linkedUserId(e.getLinkedUserId())
                .linkedMerchantId(e.getLinkedMerchantId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    // ==================== Utils ====================

    private PartnerApplication findApplication(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("998")) {
            return "+" + digits;
        }
        return digits.isBlank() ? phone.trim() : "+" + digits;
    }
}
