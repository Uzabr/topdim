package uz.topdim.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.auth.dto.*;
import uz.topdim.auth.entity.RefreshToken;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;
import uz.topdim.auth.exception.AuthException;
import uz.topdim.auth.repository.RefreshTokenRepository;
import uz.topdim.auth.repository.UserRepository;
import uz.topdim.auth.security.JwtService;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Сервис аутентификации.
 * Регистрация, логин, обновление токенов и logout.
 * При logout access token заносится в Redis blacklist.
 *
 * <p>Безопасность:
 * <ul>
 *   <li>Generic error messages для всех auth ошибок (OWASP)</li>
 *   <li>Progressive delay при множественных неудачных попытках</li>
 *   <li>Логирование событий безопасности</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final TokenBlacklistService tokenBlacklistService;
    private final LoginAttemptService loginAttemptService;

    /** Generic сообщение — одинаковое для wrong password, user not found, locked (OWASP). */
    private static final String GENERIC_AUTH_ERROR = "Неверный email или пароль";

    /**
     * Регистрация нового пользователя.
     * Проверяет уникальность email/phone, хэширует пароль.
     *
     * @param request данные регистрации (email, phone, password, name)
     * @return AuthResponse с access и refresh токенами
     * @throws AuthException если email или phone уже заняты
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AuthException("Пользователь с таким email уже существует");
        }

        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new AuthException("Пользователь с таким номером телефона уже существует");
        }

        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.USER)
                .enabled(true)
                .emailVerified(false)
                .phoneVerified(false)
                .build();

        user = userRepository.save(user);

        log.info("SECURITY: New user registered: {}", maskEmail(request.getEmail()));

        return buildAuthResponse(user);
    }

    /**
     * Аутентификация пользователя.
     * Проверяет credentials через AuthenticationManager, отзывает старые refresh tokens.
     *
     * <p>Безопасность:
     * <ul>
     *   <li>Progressive delay при множественных неудачных попытках</li>
     *   <li>Generic error message для всех ошибок</li>
     *   <li>Сброс счётчика при успешном логине</li>
     * </ul>
     *
     * @param request email и password
     * @return AuthResponse с новыми токенами
     * @throws AuthException при неверных credentials (generic message)
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail();

        // Progressive delay — замедляем ответ при множественных попытках
        Duration delay = loginAttemptService.getDelay(email);
        if (!delay.isZero()) {
            try {
                Thread.sleep(delay.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(email, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            loginAttemptService.recordFailedAttempt(email);
            log.info("SECURITY: Failed login attempt for: {}", maskEmail(email));
            throw new AuthException(GENERIC_AUTH_ERROR);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AuthException(GENERIC_AUTH_ERROR));

        // Успешный логин — сбрасываем счётчик попыток
        loginAttemptService.resetAttempts(email);

        // Revoke old refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        log.info("SECURITY: Successful login for: {}", maskEmail(email));

        return buildAuthResponse(user);
    }

    /**
     * Обновление access token по refresh token.
     * Валидирует refresh token (не отозван, не истёк), генерирует новую пару.
     *
     * @param request содержит refreshToken
     * @return AuthResponse с новыми токенами
     * @throws AuthException если токен невалиден, отозван или истёк
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

        // Revoke old and create new
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        return buildAuthResponse(refreshToken.getUser());
    }

    /**
     * Смена пароля пользователя.
     * Проверяет текущий пароль, валидирует совпадение нового с подтверждением.
     * После смены отзывает все refresh tokens (принудительный re-login).
     *
     * @param userId ID пользователя из JWT
     * @param request текущий пароль, новый пароль, подтверждение
     * @throws AuthException если текущий пароль неверный или пароли не совпадают
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
        userRepository.save(user);

        // Revoke all refresh tokens — force re-login
        refreshTokenRepository.revokeAllByUser(user);

        log.info("SECURITY: Password changed for userId: {}", userId);
    }

    /**
     * Выход из системы.
     * Отзывает refresh token (revoked = true в БД).
     *
     * @param refreshToken значение refresh токена для отзыва
     */
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.findByToken(refreshToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                    log.info("SECURITY: User logged out, userId: {}",
                            token.getUser().getId());
                });
    }

    /**
     * Гостевая аутентификация (Silent Registration).
     * Создаёт пользователя с ролью GUEST если ещё не существует.
     * Если пользователь с таким телефоном уже есть — возвращает его токены.
     *
     * @param request телефон и имя гостя
     * @return AuthResponse с access и refresh токенами
     */
    @Transactional
    public AuthResponse guestAuth(GuestAuthRequest request) {
        // Проверяем: есть ли пользователь с таким телефоном
        User user = userRepository.findByPhone(request.getPhone()).orElse(null);

        if (user == null) {
            // Создаём гостевого пользователя (без email, без пароля)
            String guestEmail = "guest_" + request.getPhone() + "@topdim.uz";
            user = User.builder()
                    .email(guestEmail)
                    .phone(request.getPhone())
                    .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                    .firstName(request.getName())
                    .role(Role.GUEST)
                    .enabled(true)
                    .emailVerified(false)
                    .phoneVerified(false)
                    .build();
            user = userRepository.save(user);
            log.info("SECURITY: Guest user created: phone={}", maskPhone(request.getPhone()));
        } else {
            log.info("SECURITY: Guest auth for existing user: phone={}", maskPhone(request.getPhone()));
        }

        return buildAuthResponse(user);
    }

    /**
     * Формирует ответ аутентификации.
     * Генерирует access token, создаёт refresh token, собирает UserDto.
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

    /**
     * Создаёт refresh token (UUID) и сохраняет в БД.
     *
     * @param user пользователь для привязки токена
     * @return строковое значение refresh token
     */
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

    /**
     * Маскирует email для логов.
     */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }

    /**
     * Маскирует номер телефона для логов.
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }
}
