package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.*;
import uz.topdim.identity.entity.Favorite;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.UserNotFoundException;
import uz.topdim.identity.repository.FavoriteRepository;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;
import uz.topdim.identity.util.EmailPlaceholders;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Сервис управления пользователями.
 * Получение/обновление профиля, управление избранным.
 * Admin: список пользователей, блокировка.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final FavoriteRepository favoriteRepository;
    private final SecurityVersionService securityVersionService;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TrustService trustService;
    private final AuditLogService auditLogService;

    // ==================== Profile ====================

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
        return mapToProfile(user);
    }

    /**
     * Обновление профиля. Телефон здесь НЕ меняется (T8a) — легаси-путь смены номера в обход
     * OTP удалён; единственный путь теперь {@code POST /api/v1/auth/phone/link}
     * (см. {@link AuthService#linkPhone(Long, String, String)}).
     */
    @Transactional
    public UserProfileResponse updateProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        if (request.getFirstName() != null) user.setFirstName(request.getFirstName());
        if (request.getLastName() != null) {
            String normalizedLastName = request.getLastName().trim();
            user.setLastName(normalizedLastName.isEmpty() ? null : normalizedLastName);
        }
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());

        user = userRepository.save(user);
        return mapToProfile(user);
    }

    // ==================== Favorites ====================

    @Transactional(readOnly = true)
    public List<FavoriteResponse> getFavorites(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::mapToFavorite)
                .collect(Collectors.toList());
    }

    @Transactional
    public FavoriteResponse addFavorite(Long userId, Long couponOfferId) {
        if (favoriteRepository.existsByUserIdAndCouponOfferId(userId, couponOfferId)) {
            throw new IllegalStateException("Купон уже в избранном");
        }
        Favorite favorite = Favorite.builder().userId(userId).couponOfferId(couponOfferId).build();
        favorite = favoriteRepository.save(favorite);
        return mapToFavorite(favorite);
    }

    @Transactional
    public void removeFavorite(Long userId, Long couponOfferId) {
        favoriteRepository.deleteByUserIdAndCouponOfferId(userId, couponOfferId);
    }

    // ==================== Admin ====================

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> getAllUsers(String role, String search, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        String normalizedSearch = search != null && !search.isBlank() ? search.trim() : null;
        Role normalizedRole = role != null && !role.isBlank()
                ? Role.valueOf(role.trim().toUpperCase())
                : null;

        Page<User> users;
        if (normalizedSearch != null && normalizedRole != null) {
            users = userRepository.searchForAdmin(normalizedRole, normalizedSearch, pageable);
        } else if (normalizedSearch != null) {
            users = userRepository.searchByEmailOrName(normalizedSearch, pageable);
        } else if (normalizedRole != null) {
            users = userRepository.findByRole(normalizedRole, pageable);
        } else {
            users = userRepository.findAll(pageable);
        }

        return users.map(this::mapToAdminUser);
    }

    @Transactional
    public AdminUserResponse blockUser(Long actorId, Long userId, boolean blocked) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));

        // Защита: нельзя блокировать админов
        if (user.getRole() == Role.ADMIN || user.getRole() == Role.SUPER_ADMIN) {
            throw new IllegalStateException("Невозможно заблокировать администратора");
        }

        if (user.isEnabled() == !blocked) {
            return mapToAdminUser(user);
        }

        user.setEnabled(!blocked);

        // Bump securityVersion — мгновенная инвалидация всех access tokens
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        user = userRepository.save(user);

        // Publish to Redis for gateway
        securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());

        // При блокировке — revoke все refresh tokens
        if (blocked) {
            refreshTokenRepository.revokeAllByUser(user);
        }

        String action = blocked ? "BLOCK_USER" : "UNBLOCK_USER";
        auditLogService.logAction(
                actorId,
                action,
                "users",
                userId,
                (blocked ? "Заблокирован" : "Разблокирован") + " пользователь: " + user.getEmail()
        );

        log.info("ADMIN: Пользователь {} (email: {}) {}, securityVersion={}",
                userId, user.getEmail(),
                blocked ? "заблокирован" : "разблокирован",
                user.getSecurityVersion());

        return mapToAdminUser(user);
    }

    @Transactional(readOnly = true)
    public AdminUserResponse getUserByIdAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("Пользователь не найден"));
        return mapToAdminUser(user);
    }

    // ==================== Payment (RabbitMQ: PaymentCompletedEvent) ====================

    /**
     * Фиксирует первую успешную оплату пользователя (→ вклад в L1 через TrustService).
     * Идемпотентно: {@code paidAt} ставится один раз и больше не перезаписывается.
     */
    @Transactional
    public void markPaid(Long userId) {
        userRepository.findById(userId).ifPresent(u -> {
            if (u.getPaidAt() == null) {
                u.setPaidAt(java.time.LocalDateTime.now());
                userRepository.save(u);
            }
        });
    }

    // ==================== Mapping ====================

    private UserProfileResponse mapToProfile(User user) {
        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .avatarUrl(user.getAvatarUrl())
                .emailVerified(user.isEmailVerified())
                .emailPlaceholder(EmailPlaceholders.isPlaceholder(user.getEmail(), user.isEmailVerified()))
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                // trustLevel — вычисляется через TrustService (phone_verified || paidAt != null),
                // НЕ читается из хранимой колонки user.trustLevel (см. AuthService.buildAuthResponse).
                .trustLevel(trustService.computeTrustLevel(user).name())
                .build();
    }

    private AdminUserResponse mapToAdminUser(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .phone(user.getPhone())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .role(user.getRole().name())
                .enabled(user.isEnabled())
                .emailVerified(user.isEmailVerified())
                .phoneVerified(user.isPhoneVerified())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private FavoriteResponse mapToFavorite(Favorite favorite) {
        return FavoriteResponse.builder()
                .id(favorite.getId())
                .couponOfferId(favorite.getCouponOfferId())
                .createdAt(favorite.getCreatedAt())
                .build();
    }
}
