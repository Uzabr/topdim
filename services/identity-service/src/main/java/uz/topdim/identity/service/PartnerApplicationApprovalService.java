package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.ApprovePartnerApplicationRequest;
import uz.topdim.identity.entity.ApplicationStatus;
import uz.topdim.identity.entity.PartnerApplication;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.PartnerApplicationRepository;
import uz.topdim.identity.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class PartnerApplicationApprovalService {

    private final PartnerApplicationRepository repository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityVersionService securityVersionService;

    /**
     * Persists a stable user link before the remote merchant call. A retry can
     * therefore reuse the same user even when the previous remote call succeeded
     * but the identity-service response or final transaction failed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Preparation prepare(Long id, ApprovePartnerApplicationRequest request) {
        PartnerApplication app = findLocked(id);

        if (app.getStatus() == ApplicationStatus.APPROVED) {
            if (app.getLinkedUserId() == null || app.getLinkedMerchantId() == null) {
                throw new IllegalStateException("Approved application has incomplete links");
            }
            return Preparation.completed(app);
        }
        if (app.getStatus() == ApplicationStatus.REJECTED) {
            throw new IllegalStateException("Rejected application cannot be approved");
        }
        if (app.getStatus() == ApplicationStatus.PROCESSING) {
            User linkedUser = linkedUser(app);
            validateRetryIdentity(app, linkedUser, request);
            applyApprovalDetails(app, request);
            repository.save(app);
            return Preparation.pending(linkedUser.getId());
        }

        User partner = createOrPromotePartnerUser(app, request);
        app.setStatus(ApplicationStatus.PROCESSING);
        app.setLinkedUserId(partner.getId());
        app.setRejectionReason(null);
        applyApprovalDetails(app, request);
        repository.save(app);
        return Preparation.pending(partner.getId());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PartnerApplication complete(Long id, Long adminId, Long merchantId) {
        PartnerApplication app = findLocked(id);
        if (app.getStatus() == ApplicationStatus.APPROVED
                && Objects.equals(app.getLinkedMerchantId(), merchantId)) {
            return app;
        }
        if (app.getStatus() != ApplicationStatus.PROCESSING) {
            throw new IllegalStateException("Only PROCESSING application can be completed");
        }

        app.setStatus(ApplicationStatus.APPROVED);
        app.setReviewedBy(adminId);
        app.setReviewedAt(LocalDateTime.now());
        app.setLinkedMerchantId(merchantId);
        return repository.save(app);
    }

    private User createOrPromotePartnerUser(
            PartnerApplication app,
            ApprovePartnerApplicationRequest request
    ) {
        String email = normalizeEmail(request.getLoginEmail());
        String phone = normalizePhone(request.getPhone());

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        User userByPhone = phone != null ? userRepository.findByPhone(phone).orElse(null) : null;

        if (user != null && userByPhone != null && !user.getId().equals(userByPhone.getId())) {
            throw new IllegalStateException("Phone is already linked to another user");
        }
        if (user == null) {
            user = userByPhone;
        }
        if (user == null) {
            if (request.getTemporaryPassword() == null || request.getTemporaryPassword().isBlank()) {
                throw new IllegalStateException("Temporary password is required for a new partner account");
            }
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
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN
                || user.getRole() == Role.MODERATOR) {
            throw new IllegalStateException("Admin or moderator account cannot be linked as partner");
        }

        boolean userChanged = false;
        String existingEmail = normalizeEmail(user.getEmail());
        if (existingEmail == null || existingEmail.isBlank()) {
            user.setEmail(email);
            userChanged = true;
        } else if (!existingEmail.equals(email)) {
            throw new IllegalStateException("Existing phone account has a different email");
        }

        String existingPhone = normalizePhone(user.getPhone());
        if (existingPhone == null || existingPhone.isBlank()) {
            user.setPhone(phone);
            userChanged = true;
        } else if (!Objects.equals(existingPhone, phone)) {
            throw new IllegalStateException("Existing email account has a different phone");
        }

        boolean securityVersionChanged = false;
        if (user.getRole() != Role.PARTNER) {
            user.setRole(Role.PARTNER);
            user.setSecurityVersion(user.getSecurityVersion() + 1);
            userChanged = true;
            securityVersionChanged = true;
        }
        if (userChanged) {
            user = userRepository.save(user);
        }
        if (securityVersionChanged) {
            securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());
        }
        return user;
    }

    private User linkedUser(PartnerApplication app) {
        if (app.getLinkedUserId() == null) {
            throw new IllegalStateException("Processing application has no linked user");
        }
        return userRepository.findById(app.getLinkedUserId())
                .orElseThrow(() -> new IllegalStateException("Linked partner user not found"));
    }

    private void validateRetryIdentity(
            PartnerApplication app,
            User linkedUser,
            ApprovePartnerApplicationRequest request
    ) {
        String requestedEmail = normalizeEmail(request.getLoginEmail());
        String requestedPhone = normalizePhone(request.getPhone());
        if (!requestedEmail.equals(normalizeEmail(app.getEmail()))
                || !requestedEmail.equals(normalizeEmail(linkedUser.getEmail()))
                || !Objects.equals(requestedPhone, normalizePhone(app.getPhone()))
                || !Objects.equals(requestedPhone, normalizePhone(linkedUser.getPhone()))) {
            throw new IllegalStateException("Approval already started with different login or phone");
        }
    }

    private void applyApprovalDetails(
            PartnerApplication app,
            ApprovePartnerApplicationRequest request
    ) {
        app.setCompanyName(request.getMerchantName().trim());
        app.setAddress(request.getAddress().trim());
        app.setCity(blankToNull(request.getCity()));
        app.setWorkingHours(blankToNull(request.getWorkingHours()));
        app.setWebsite(blankToNull(request.getWebsite()));
        app.setEmail(normalizeEmail(request.getLoginEmail()));
        app.setPhone(normalizePhone(request.getPhone()));
    }

    private PartnerApplication findLocked(Long id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Application not found: " + id));
    }

    private String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("998")) {
            return "+" + digits;
        }
        return digits.isBlank() ? phone.trim() : "+" + digits;
    }

    public record Preparation(Long userId, PartnerApplication completedApplication) {
        static Preparation pending(Long userId) {
            return new Preparation(userId, null);
        }

        static Preparation completed(PartnerApplication application) {
            return new Preparation(application.getLinkedUserId(), application);
        }

        public boolean alreadyCompleted() {
            return completedApplication != null;
        }
    }
}
