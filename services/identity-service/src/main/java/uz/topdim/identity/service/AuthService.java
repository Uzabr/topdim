package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.dto.*;
import uz.topdim.identity.entity.RefreshToken;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.TrustLevel;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;
import uz.topdim.identity.security.GoogleTokenVerifier;
import uz.topdim.identity.security.JwtService;
import uz.topdim.identity.security.TelegramLoginVerifier;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Сервис аутентификации.
 * Регистрация, логин, refresh, logout, change password, phone-OTP вход/восстановление.
 * (Outbox pattern удалён — профиль создаётся в той же БД.)
 * <p>Гостевой вход (небезопасный, без проверки владения) депрекирован в T5 —
 * см. {@code AuthController#guestAuth} (410 Gone); бизнес-логика удалена отсюда.
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
    private final TelegramLoginVerifier telegramLoginVerifier;
    private final TrustService trustService;
    private final OtpService otpService;
    private final AccountResolutionService accountResolutionService;
    private final GoogleTokenVerifier googleTokenVerifier;

    /**
     * Регистрация нового пользователя.
     * Проверяет уникальность email/phone, создаёт User с ролью USER.
     * Профиль создаётся сразу — outbox не нужен.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());
        String normalizedPhone = normalizePhone(request.getPhone());

        // M5: единый нейтральный ответ — не раскрываем, какое именно поле занято (anti-enumeration)
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)
                || (normalizedPhone != null && userRepository.existsByPhone(normalizedPhone))) {
            throw new AuthException("Не удалось зарегистрироваться с указанными данными");
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

        if (!user.isEnabled() || user.isDeleted()) {
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
        String tokenHash = PasswordResetService.sha256(request.getRefreshToken());
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(tokenHash)
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
            refreshTokenRepository.findByTokenHash(PasswordResetService.sha256(refreshToken))
                    .ifPresent(token -> {
                        token.setRevoked(true);
                        refreshTokenRepository.save(token);
                        log.info("SECURITY: User logged out, userId: {}",
                                token.getUser().getId());
                    });
        }
    }

    /**
     * Телефон-OTP вход (T5). Единый путь вход/регистрация/восстановление по спеку:
     * {@link AccountResolutionService#resolveByPhone(String)} находит существующего
     * пользователя ИЛИ создаёт нового (phone уже доказан успешным OTP).
     * <p>Гонка find→save при создании не обрабатывается отдельно: OTP одноразовый
     * ({@link OtpService#verifyOtp} удаляет ключ при первом успехе), поэтому повторный
     * confirm с тем же кодом падает на verifyOtp раньше resolveByPhone.
     */
    @Transactional
    public AuthResponse phoneAuth(String phone, String code) {
        if (!otpService.verifyOtp(phone, code)) {
            throw new AuthException("Неверный или просроченный код");
        }

        User user = accountResolutionService.resolveByPhone(phone);

        if (!user.isEnabled() || user.isDeleted()) {
            throw new AuthException("Аккаунт недоступен");
        }

        refreshTokenRepository.revokeAllByUser(user);

        log.info("SECURITY: Phone OTP auth for userId: {}", user.getId());

        return buildAuthResponse(user);
    }

    /**
     * Авторизация/регистрация через Telegram Login Widget.
     * Проверяет подпись (HMAC) и свежесть {@code auth_date}, затем логинит
     * существующего пользователя по {@code telegram_chat_id} или создаёт нового
     * с уровнем доверия {@link TrustLevel#L0} (без перков до верификации).
     */
    @Transactional
    public AuthResponse telegramAuth(TelegramAuthRequest request) {
        telegramLoginVerifier.verify(request);

        User user = userRepository.findByTelegramChatId(request.getId()).orElse(null);
        if (user == null) {
            user = User.builder()
                    .email("tg_" + request.getId() + "@topdim.uz")
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .firstName(nonBlankOr(request.getFirstName(), "Пользователь"))
                    .lastName(request.getLastName())
                    .role(Role.USER)
                    .enabled(true)
                    .emailVerified(false)
                    .phoneVerified(false)
                    .telegramChatId(request.getId())
                    .telegramUsername(request.getUsername())
                    .telegramLinkedAt(LocalDateTime.now())
                    .trustLevel(TrustLevel.L0)
                    .build();
            user = userRepository.save(user);
            log.info("SECURITY: Telegram user created: tgId={}", request.getId());
        } else {
            if (!user.isEnabled() || user.isDeleted()) {
                throw new AuthException("Аккаунт заблокирован");
            }
            // Держим username актуальным (в Telegram он может меняться).
            if (request.getUsername() != null
                    && !request.getUsername().equals(user.getTelegramUsername())) {
                user.setTelegramUsername(request.getUsername());
            }
            refreshTokenRepository.revokeAllByUser(user);
            log.info("SECURITY: Telegram auth for existing user: tgId={}", request.getId());
        }
        return buildAuthResponse(user);
    }

    /**
     * Авторизация/регистрация через Google (ID-token из Google Identity Services).
     * Верификация токена — {@link GoogleTokenVerifier#verify(String)} (бросает
     * {@link AuthException} при невалидном/просроченном токене). Резолюция аккаунта
     * (по google_sub → вход; по подтверждённому email → привязка; иначе — создание)
     * полностью в {@link AccountResolutionService#resolveByGoogle}, логика не дублируется.
     * Как и в {@code phoneAuth}: сервис не знает, новый пользователь или существующий,
     * поэтому revoke старых refresh-токенов безусловен (для нового пользователя — no-op).
     */
    @Transactional
    public AuthResponse googleAuth(String idToken) {
        GoogleIdentity identity = googleTokenVerifier.verify(idToken);

        User user = accountResolutionService.resolveByGoogle(
                identity.sub(), identity.email(), identity.emailVerified());

        if (!user.isEnabled() || user.isDeleted()) {
            throw new AuthException("Аккаунт недоступен");
        }

        refreshTokenRepository.revokeAllByUser(user);

        log.info("SECURITY: Google auth for userId: {}", user.getId());

        return buildAuthResponse(user);
    }

    private static String nonBlankOr(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    /**
     * Формирует ответ аутентификации.
     * trustLevel — вычисляется через {@link TrustService} (phone_verified || paidAt != null),
     * НЕ читается из хранимой колонки {@code user.trustLevel} (deprecated, не источник правды).
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
                        .trustLevel(trustService.computeTrustLevel(user).name())
                        .build())
                .build();
    }

    private String createRefreshToken(User user) {
        String plainToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(PasswordResetService.sha256(plainToken))
                .user(user)
                .expiresAt(Instant.now().plusMillis(jwtService.getRefreshTokenExpiration()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
        return plainToken;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
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
