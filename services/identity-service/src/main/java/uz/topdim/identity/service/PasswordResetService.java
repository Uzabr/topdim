package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.entity.AuthActionToken;
import uz.topdim.identity.entity.AuthActionType;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.repository.AuthActionTokenRepository;
import uz.topdim.identity.repository.RefreshTokenRepository;
import uz.topdim.identity.repository.UserRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Бизнес-логика сброса пароля.
 *
 * <p>Флоу:
 * <ol>
 *   <li>requestReset(email) — создаёт одноразовый токен, отправляет на email</li>
 *   <li>confirmReset(token, newPassword) — проверяет токен, меняет пароль, bump securityVersion</li>
 * </ol>
 *
 * <p>Безопасность:
 * <ul>
 *   <li>Request всегда возвращает 202 (защита от enumeration)</li>
 *   <li>Токен хранится как SHA-256 hash</li>
 *   <li>Одноразовый: после использования помечается used</li>
 *   <li>Старые токены revoke'ятся при создании нового</li>
 *   <li>TTL: 30 минут</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;
    private final AuthActionTokenRepository actionTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityVersionService securityVersionService;
    private final NotificationSender notificationSender;

    private static final int TOKEN_EXPIRATION_MINUTES = 30;

    /**
     * Запрос сброса пароля.
     * Всегда возвращает без ошибки (защита от enumeration).
     * Если пользователь не найден — молча игнорируем.
     */
    @Transactional
    public void requestReset(String email) {
        String normalizedEmail = email.toLowerCase().trim();

        userRepository.findByEmailIgnoreCase(normalizedEmail).ifPresent(user -> {
            // Revoke все активные PASSWORD_RESET токены этого пользователя
            actionTokenRepository.revokeAllActiveByUserIdAndType(user.getId(), AuthActionType.PASSWORD_RESET);

            // Генерируем токен
            String plainToken = UUID.randomUUID().toString();
            String tokenHash = sha256(plainToken);

            AuthActionToken actionToken = AuthActionToken.builder()
                    .userId(user.getId())
                    .type(AuthActionType.PASSWORD_RESET)
                    .tokenHash(tokenHash)
                    .target(normalizedEmail)
                    .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES))
                    .lastSentAt(LocalDateTime.now())
                    .build();

            actionTokenRepository.save(actionToken);

            // Отправляем токен
            notificationSender.sendPasswordResetToken(normalizedEmail, plainToken);

            log.info("SECURITY: Password reset requested for userId={}", user.getId());
        });
    }

    /**
     * Подтверждение сброса пароля.
     * Проверяет: токен одноразовый, неистёкший, неиспользованный, правильного типа.
     * После успеха: меняет пароль, помечает token used, revoke all refresh tokens, bump securityVersion.
     */
    @Transactional
    public void confirmReset(String token, String newPassword) {
        String tokenHash = sha256(token);

        AuthActionToken actionToken = actionTokenRepository
                .findByTokenHashAndType(tokenHash, AuthActionType.PASSWORD_RESET)
                .orElseThrow(() -> new AuthException("Недействительный или просроченный токен сброса пароля"));

        // Проверяем валидность
        if (!actionToken.isValid()) {
            throw new AuthException("Недействительный или просроченный токен сброса пароля");
        }

        // Находим пользователя
        User user = userRepository.findById(actionToken.getUserId())
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        if (!user.isEnabled()) {
            throw new AuthException("Пользователь заблокирован");
        }

        // Меняем пароль
        user.setPassword(passwordEncoder.encode(newPassword));

        // Bump securityVersion — инвалидирует все access tokens
        user.setSecurityVersion(user.getSecurityVersion() + 1);
        userRepository.save(user);

        // Publish to Redis
        securityVersionService.publishSecurityVersion(user.getId(), user.getSecurityVersion());

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllByUser(user);

        // Помечаем токен использованным
        actionToken.setUsedAt(LocalDateTime.now());
        actionTokenRepository.save(actionToken);

        log.info("SECURITY: Password reset confirmed for userId={}, securityVersion={}",
                user.getId(), user.getSecurityVersion());
    }

    /**
     * SHA-256 hash для хранения токена (не bcrypt — нужен deterministic lookup).
     */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
