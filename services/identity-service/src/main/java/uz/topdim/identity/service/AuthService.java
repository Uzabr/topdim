package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.*;
import uz.topdim.identity.entity.RefreshToken;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;
import uz.topdim.identity.security.JwtService;

import java.time.Instant;
import java.util.UUID;

/**
 * Сервис аутентификации.
 * Регистрация, логин, refresh, logout, change password, guest auth.
 * (Outbox pattern удалён — профиль создаётся в той же БД.)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final LoginAttemptService loginAttemptService;
    private final TokenBlacklistService tokenBlacklistService;
    private final SecurityVersionService securityVersionService;

    /**
     * Регистрация нового пользователя.
     * Проверяет уникальность email/phone, создаёт User с ролью USER.
     * Профиль создаётся сразу — outbox не нужен.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new AuthException("Email уже зарегистрирован");
        }

        if (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone)) {
            throw new AuthException("Телефон уже зарегистрирован");
        }

        User user = User.builder()
                .email(normalizedEmail)
                .phone(normalizedPhone)
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.USER)
                .enabled(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();

        user = userRepository.save(user);

        log.info("SECURITY: New user registered: {}", maskEmail(normalizedEmail));

        return buildAuthResponse(user);
    }

    /**
     * Вход в систему.
     * Проверяет email + password, применяет progressive delay.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = normalizeEmail(request.getEmail());

        // Progressive delay (brute-force protection)
        java.time.Duration delay = loginAttemptService.getDelay(email);
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> {
                    loginAttemptService.recordFailedAttempt(email);
                    return new AuthException("Неверный email или пароль");
                });

        if (!user.isEnabled()) {
            loginAttemptService.recordFailedAttempt(email);
            throw new AuthException("Неверный email или пароль");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailedAttempt(email);
            throw new AuthException("Неверный email или пароль");
        }

        // Успешный вход — сброс счётчика
        loginAttemptService.resetAttempts(email);

        // Revoke old refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        log.info("SECURITY: Successful login for: {}", maskEmail(email));

        return buildAuthResponse(user);
    }

    /**
     * Обновление access token по refresh token.
     */
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new AuthException("Невалидный refresh token"));

        if (refreshToken.isRevoked()) {
            throw new AuthException("Refresh token отозван");
        }

        if (refreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AuthException("Refresh token истёк");
        }

        if (!refreshToken.getUser().isEnabled()) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new AuthException("Пользователь заблокирован");
        }

        // Revoke old and create new
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return buildAuthResponse(refreshToken.getUser());
    }

    /**
     * Смена пароля пользователя.
     * Bump securityVersion → все ранее выданные access tokens мгновенно невалидны.
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new AuthException("Неверный текущий пароль");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AuthException("Новый пароль и подтверждение не совпадают");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        // Bump securityVersion — инвалидирует все access tokens
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userRepository.save(user);

        // Publish to Redis for gateway
        securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());

        // Revoke all refresh tokens — force re-login
        refreshTokenRepository.revokeAllByUser(user);

        log.info("SECURITY: Password changed for userId: {}, securityVersion bumped to {}",
                userId, user.getSecurityVersion());
    }

    /**
     * Выход из системы.
     * 1) Blacklist текущий access token по jti (мгновенная инвалидация)
     * 2) Revoke refresh token
     *
     * @param accessToken текущий access token (для blacklist jti)
     * @param refreshToken refresh token (для revoke)
     */
    @Transactional
    public void logout(String accessToken, String refreshToken) {
        // Blacklist access token по jti
        if (accessToken != null) {
            try {
                String jti = jwtService.extractJti(accessToken);
                long remainingMs = jwtService.getRemainingExpiration(accessToken);
                if (jti != null && remainingMs > 0) {
                    tokenBlacklistService.blacklist(jti, remainingMs);
                    log.info("SECURITY: Access token jti={} blacklisted, TTL={}ms", jti, remainingMs);
                }
            } catch (Exception e) {
                log.warn("SECURITY: Failed to blacklist access token on logout: {}", e.getMessage());
            }
        }

        // Revoke refresh token
        if (refreshToken != null) {
            refreshTokenRepository.findByToken(refreshToken)
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                        log.info("SECURITY: User logged out, userId: {}",
                                token.getUser().getId());
                    });
        }
    }

    /**
     * Гостевая аутентификация (Silent Registration).
     * Создаёт пользователя с ролью GUEST если ещё не существует.
     */
    @Transactional
    public AuthResponse guestAuth(GuestAuthRequest request) {
        String normalizedPhone = normalizePhone(request.getPhone());
        User user = userRepository.findByPhone(normalizedPhone).orElse(null);

        if (user == null) {
            String guestEmail = "guest_" + normalizedPhone + "@topdim.uz";
            user = User.builder()
                    .email(guestEmail)
                    .phone(normalizedPhone)
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .firstName(request.getName())
                    .role(Role.GUEST)
                    .enabled(true)
                    .emailVerified(false)
                    .phoneVerified(false)
                    .build();
            user = userRepository.save(user);

            log.info("SECURITY: Guest user created: phone={}", maskPhone(normalizedPhone));
        } else {
            if (user.getRole() != Role.GUEST) {
                throw new AuthException("Этот номер уже привязан к зарегистрированному пользователю");
            }
            if (!user.isEnabled()) {
                throw new AuthException("Гостевой аккаунт заблокирован");
            }
            log.info("SECURITY: Guest auth for existing guest user: phone={}", maskPhone(normalizedPhone));
        }

        return buildAuthResponse(user);
    }

    /**
     * Формирует ответ аутентификации.
     */
    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshTokenStr = createRefreshToken(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpiration() / 1000)
                .user(AuthResponse.UserDto.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .phone(user.getPhone())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole().name())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .build();
    }

    private String createRefreshToken(User user) {
        String token = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    private String normalizeEmail(String email) {
        return email.toLowerCase().trim();
    }

    private String normalizePhone(String phone) {
        if (phone == null) {
            return null;
        }
        String normalized = phone.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
