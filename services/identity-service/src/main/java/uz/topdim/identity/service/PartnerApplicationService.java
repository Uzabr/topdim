package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.repository.PartnerApplicationRepository;
import uz.topdim.identity.repository.UserRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PartnerApplicationService {

    private final PartnerApplicationRepository repository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CouponMerchantClient couponMerchantClient;
    private final SecurityVersionService securityVersionService;

    // ==================== Submit ====================

    @Transactional
    public PartnerApplicationResponse submit(PartnerApplicationRequest request) {
        String phone = normalizePhone(request.getPhone());
        if (repository.existsByPhoneAndStatus(phone, ApplicationStatus.PENDING)) {
            throw new IllegalStateException("pending partner application already exists for phone");
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
        return toResponse(repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found: " + id)));
    }

    // ==================== Approve ====================

    @Transactional
    public PartnerApplicationResponse approve(Long id, Long adminId, ApprovePartnerApplicationRequest request) {
        PartnerApplication app = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found: " + id));

        if (app.getStatus() != ApplicationStatus.PENDING) {
            throw new IllegalStateException("Only PENDING application can be approved");
        }

        User partner = createOrPromotePartnerUser(app, request);

        CreateMerchantOnboardingRequest merchantRequest = CreateMerchantOnboardingRequest.builder()
                .userId(partner.getId())
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

        app.setStatus(ApplicationStatus.APPROVED);
        app.setReviewedBy(adminId);
        app.setReviewedAt(LocalDateTime.now());
        app.setLinkedUserId(partner.getId());
        app.setLinkedMerchantId(merchant.getId());
        app.setRejectionReason(null);

        return toResponse(repository.save(app));
    }

    private User createOrPromotePartnerUser(PartnerApplication app, ApprovePartnerApplicationRequest request) {
        String email = request.getLoginEmail().trim().toLowerCase();
        String phone = normalizePhone(request.getPhone());

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email(email)
                    .phone(phone)
                    .password(passwordEncoder.encode(request.getTemporaryPassword()))
                    .firstName(app.getFirstName())
                    .lastName(app.getLastName())
                    .role(Role.PARTNER)
                    .enabled(true)
                    .emailVerified(false)
                    .phoneVerified(false)
                    .build();
            return userRepository.save(user);
        }

        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN || user.getRole() == Role.MODERATOR) {
            throw new IllegalStateException("Admin or moderator account cannot be linked as partner");
        }

        if (user.getRole() != Role.PARTNER) {
            user.setRole(Role.PARTNER);
            user.setSecurityVersion(user.getSecurityVersion() + 1);
            user = userRepository.save(user);
            securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());
        }

        return user;
    }

    // ==================== Reject ====================

    @Transactional
    public PartnerApplicationResponse reject(Long id, Long adminId, RejectPartnerApplicationRequest request) {
        PartnerApplication app = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Application not found: " + id));

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
