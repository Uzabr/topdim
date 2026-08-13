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
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.StaffRepository;
import uz.topdim.identity.repository.UserRepository;
import uz.topdim.identity.validation.StrongPasswordValidator;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerStaffService {
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SecurityVersionService securityVersionService;
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

        String role = normalizeStaffRole(request.getRole());
        validateStaffRequest(request, role);
        if (isCashierRole(role)
                && !isActiveMerchantLocation(userId, request.getMerchantLocationId())) {
            throw new IllegalArgumentException("Филиал не принадлежит мерчанту или не активен");
        }

        String loginEmail = request.getLoginEmail().trim().toLowerCase(Locale.ROOT);

        Staff staff = Staff.builder()
                .userId(userId)
                .name(request.getName())
                .phone(request.getPhone())
                .role(role)
                .merchantId(merchantId)
                .merchantLocationId(isCashierRole(role) ? request.getMerchantLocationId() : null)
                .active(true)
                .build();

        if (userRepository.existsByEmailIgnoreCase(loginEmail)) {
            throw new IllegalArgumentException("Пользователь с таким email уже существует");
        }
        User loginUser = User.builder()
                .email(loginEmail)
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

        staff = staffRepository.save(staff);
        log.info("PARTNER: userId={} добавил сотрудника {} ({}) для мерчанта {}", userId, staff.getId(), staff.getPhone(), merchantId);
        return mapToResponse(staff);
    }

    @Transactional
    public void removeStaff(Long userId, Long staffId) {
        resolveMerchantId(userId);
        Staff staff = staffRepository.findByUserIdAndId(userId, staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Сотрудник не найден или не принадлежит вам"));

        User loginUser = null;
        if (staff.getLoginUserId() != null) {
            loginUser = userRepository.findByIdForUpdate(staff.getLoginUserId())
                    .orElseThrow(() -> new ResourceNotFoundException("Учётная запись сотрудника не найдена"));
        }

        if (staff.isActive()) {
            staff.setActive(false);
            staffRepository.save(staff);
        }

        if (loginUser != null) {
            if (loginUser.isEnabled()) {
                loginUser.setEnabled(false);
                loginUser.setSecurityVersion(loginUser.getSecurityVersion() + 1);
                loginUser = userRepository.save(loginUser);
                securityVersionService.publishSecurityVersion(loginUser.getId(), loginUser.getSecurityVersion());
            }
            refreshTokenRepository.revokeAllByUser(loginUser);
        }

        log.info("PARTNER: userId={} деактивировал сотрудника {} и отозвал его сессии", userId, staffId);
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
            String role = normalizeStaffRole(staff.getRole());
            if (!staff.isActive()) {
                throw new IllegalStateException("Сотрудник деактивирован. Обратитесь к владельцу бизнеса.");
            }
            if ("CASHIER".equals(role) && staff.getMerchantLocationId() == null) {
                throw new IllegalStateException("Кассир не привязан к филиалу. Обратитесь к владельцу бизнеса.");
            }
            Long activeMerchantId = resolveMerchantId(staff.getUserId());
            if (staff.getMerchantId() == null || !staff.getMerchantId().equals(activeMerchantId)) {
                throw new IllegalStateException("Контекст сотрудника не соответствует активному мерчанту");
            }
            if ("CASHIER".equals(role)
                    && !isActiveMerchantLocation(staff.getUserId(), staff.getMerchantLocationId())) {
                throw new IllegalStateException("Филиал кассира не принадлежит мерчанту или не активен");
            }
            boolean canViewDashboard = "MANAGER".equals(role);
            return PartnerAccessContextResponse.builder()
                    .role(role)
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

    @Transactional(readOnly = true)
    public Set<Long> getActiveStaffLocationIds(Long merchantId) {
        return staffRepository.findActiveLocationIdsByMerchantId(merchantId);
    }

    /**
     * Every staff member must have an independent login. Cashiers additionally require a location.
     */
    private void validateStaffRequest(CreateStaffRequest request, String role) {
        if ("CASHIER".equals(role)) {
            if (request.getMerchantLocationId() == null) {
                throw new IllegalArgumentException("Кассир должен быть привязан к филиалу");
            }
        }
        if (request.getLoginEmail() == null || request.getLoginEmail().isBlank()) {
            throw new IllegalArgumentException("Для сотрудника обязателен email для входа");
        }
        if (!StrongPasswordValidator.isStrong(request.getTemporaryPassword())) {
            throw new IllegalArgumentException("Укажите надёжный временный пароль для сотрудника");
        }
    }

    private String normalizeStaffRole(String rawRole) {
        String role = rawRole == null ? "CASHIER" : rawRole.trim().toUpperCase(Locale.ROOT);
        if (!Set.of("CASHIER", "MANAGER").contains(role)) {
            throw new IllegalArgumentException("Допустимы роли CASHIER или MANAGER");
        }
        return role;
    }

    private boolean isCashierRole(String role) {
        return "CASHIER".equals(role);
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
