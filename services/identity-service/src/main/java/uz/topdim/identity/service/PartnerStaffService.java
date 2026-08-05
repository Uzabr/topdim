package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.identity.client.CouponMerchantClient;
import uz.topdim.identity.client.MerchantLocationResponse;
import uz.topdim.identity.client.MerchantOnboardingResponse;
import uz.topdim.identity.dto.CreateStaffRequest;
import uz.topdim.identity.dto.PartnerAccessContextResponse;
import uz.topdim.identity.dto.PartnerStaffResponse;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.Staff;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.ResourceNotFoundException;
import uz.topdim.identity.repository.StaffRepository;
import uz.topdim.identity.repository.UserRepository;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerStaffService {
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CouponMerchantClient couponMerchantClient;

    @Transactional(readOnly = true)
    public List<PartnerStaffResponse> getMyStaff(Long userId) {
        resolveMerchantId(userId);
        return staffRepository.findByUserId(userId).stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public PartnerStaffResponse addStaff(Long userId, CreateStaffRequest request) {
        // Resolve merchant for this owner
        Long merchantId = resolveMerchantId(userId);

        // Beta safety: validate cashier requirements
        validateStaffRequest(request);
        if (isCashierRole(request.getRole())
                && !isActiveMerchantLocation(userId, request.getMerchantLocationId())) {
            throw new IllegalArgumentException("Филиал не принадлежит мерчанту или не активен");
        }

        Staff staff = Staff.builder()
                .userId(userId)
                .name(request.getName())
                .phone(request.getPhone())
                .role(request.getRole() != null ? request.getRole() : "CASHIER")
                .merchantId(merchantId)
                .merchantLocationId(request.getMerchantLocationId())
                .active(true)
                .build();

        // Create login user for cashier if loginEmail and password provided
        if (request.getLoginEmail() != null && request.getTemporaryPassword() != null) {
            if (userRepository.existsByEmailIgnoreCase(request.getLoginEmail())) {
                throw new IllegalArgumentException("Пользователь с таким email уже существует");
            }
            User loginUser = User.builder()
                    .email(request.getLoginEmail().toLowerCase())
                    .password(passwordEncoder.encode(request.getTemporaryPassword()))
                    .firstName(request.getName())
                    .phone(request.getPhone())
                    .role(Role.PARTNER)
                    .enabled(true)
                    .emailVerified(true)
                    .build();
            loginUser = userRepository.save(loginUser);
            staff.setLoginUserId(loginUser.getId());
            log.info("PARTNER: created login user {} for staff of owner {}", loginUser.getId(), userId);
        }

        staff = staffRepository.save(staff);
        log.info("PARTNER: userId={} добавил сотрудника {} ({}) для мерчанта {}", userId, staff.getId(), staff.getPhone(), merchantId);
        return mapToResponse(staff);
    }

    @Transactional
    public void removeStaff(Long userId, Long staffId) {
        resolveMerchantId(userId);
        Staff staff = staffRepository.findByUserIdAndId(userId, staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден или не принадлежит вам"));
        staff.setActive(false);
        staffRepository.save(staff);
        log.info("PARTNER: userId={} деактивировал сотрудника {}", userId, staffId);
    }

    /**
     * Определяет контекст доступа партнёра по userId.
     * Owner: пользователь является владельцем мерчанта (Merchant.userId == userId).
     * Cashier: пользователь залогинен через staff.loginUserId.
     */
    @Transactional(readOnly = true)
    public PartnerAccessContextResponse resolveAccessContext(Long userId) {
        // 1. Проверяем — это кассир? (staff с loginUserId == userId)
        var staffOpt = staffRepository.findByLoginUserId(userId);
        if (staffOpt.isPresent()) {
            Staff staff = staffOpt.get();
            if (!staff.isActive()) {
                throw new IllegalStateException("Сотрудник деактивирован. Обратитесь к владельцу бизнеса.");
            }
            if ("CASHIER".equals(staff.getRole()) && staff.getMerchantLocationId() == null) {
                throw new IllegalStateException("Кассир не привязан к филиалу. Обратитесь к владельцу бизнеса.");
            }
            Long activeMerchantId = resolveMerchantId(staff.getUserId());
            if (staff.getMerchantId() == null || !staff.getMerchantId().equals(activeMerchantId)) {
                throw new IllegalStateException("Контекст сотрудника не соответствует активному мерчанту");
            }
            if ("CASHIER".equals(staff.getRole())
                    && !isActiveMerchantLocation(staff.getUserId(), staff.getMerchantLocationId())) {
                throw new IllegalStateException("Филиал кассира не принадлежит мерчанту или не активен");
            }
            boolean canViewDashboard = "MANAGER".equals(staff.getRole());
            return PartnerAccessContextResponse.builder()
                    .role(staff.getRole())
                    .merchantId(activeMerchantId)
                    .merchantLocationId(staff.getMerchantLocationId())
                    .staffId(staff.getId())
                    .staffName(staff.getName())
                    .canViewDashboard(canViewDashboard)
                    .canRedeem(true)
                    .build();
        }

        // 2. Не кассир — пробуем как Owner через coupon-service merchant context
        Long merchantId = resolveMerchantId(userId);
        return PartnerAccessContextResponse.builder()
                .role("OWNER")
                .merchantId(merchantId)
                .merchantLocationId(null)
                .staffId(null)
                .staffName(null)
                .canViewDashboard(true)
                .canRedeem(true)
                .build();
    }

    /**
     * Beta safety: cashier must have location, login email, and temporary password.
     * Without these, the cashier cannot log in or could redeem at the wrong branch.
     */
    private void validateStaffRequest(CreateStaffRequest request) {
        String role = request.getRole() != null ? request.getRole().trim().toUpperCase() : "CASHIER";
        if ("CASHIER".equals(role)) {
            if (request.getMerchantLocationId() == null) {
                throw new IllegalArgumentException("Кассир должен быть привязан к филиалу");
            }
            if (request.getLoginEmail() == null || request.getLoginEmail().isBlank()) {
                throw new IllegalArgumentException("Для кассира обязателен email для входа");
            }
            if (request.getTemporaryPassword() == null || request.getTemporaryPassword().length() < 6) {
                throw new IllegalArgumentException("Временный пароль кассира должен быть не короче 6 символов");
            }
        }
    }

    private boolean isCashierRole(String role) {
        return role == null || "CASHIER".equals(role.trim().toUpperCase());
    }

    private boolean isActiveMerchantLocation(Long ownerUserId, Long locationId) {
        try {
            ApiResponse<List<MerchantLocationResponse>> response =
                    couponMerchantClient.getMerchantLocationsByUserId(ownerUserId);
            List<MerchantLocationResponse> locations =
                    response != null && response.getData() != null ? response.getData() : List.of();
            return locations.stream()
                    .anyMatch(location -> location.isActive() && locationId.equals(location.getId()));
        } catch (Exception e) {
            log.warn("Не удалось проверить филиал {} для ownerUserId={}: {}",
                    locationId, ownerUserId, e.getMessage());
            throw new IllegalStateException("Не удалось проверить филиал сотрудника");
        }
    }

    private Long resolveMerchantId(Long userId) {
        MerchantOnboardingResponse merchant;
        try {
            ApiResponse<MerchantOnboardingResponse> response = couponMerchantClient.getMerchantByUserId(userId);
            merchant = response != null ? response.getData() : null;
        } catch (Exception e) {
            log.warn("Не удалось получить мерчанта для userId={}: {}", userId, e.getMessage());
            throw new ResourceNotFoundException("Мерчант для пользователя не найден");
        }
        if (merchant == null || merchant.getId() == null) {
            throw new ResourceNotFoundException("Мерчант для пользователя не найден");
        }
        if (!merchant.isActive()) {
            throw new IllegalStateException("Мерчант не активен");
        }
        return merchant.getId();
    }

    private PartnerStaffResponse mapToResponse(Staff staff) {
        String loginEmail = null;
        if (staff.getLoginUserId() != null) {
            loginEmail = userRepository.findById(staff.getLoginUserId())
                    .map(User::getEmail).orElse(null);
        }
        return PartnerStaffResponse.builder()
                .id(staff.getId())
                .name(staff.getName())
                .phone(staff.getPhone())
                .role(staff.getRole())
                .active(staff.isActive())
                .merchantLocationId(staff.getMerchantLocationId())
                .loginEmail(loginEmail)
                .createdAt(staff.getCreatedAt())
                .build();
    }
}
