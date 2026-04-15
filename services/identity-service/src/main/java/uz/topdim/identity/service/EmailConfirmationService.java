package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.identity.entity.AuthActionToken;
import uz.topdim.identity.entity.AuthActionType;
import uz.topdim.identity.entity.User;
import uz.topdim.identity.exception.AuthException;
import uz.topdim.identity.repository.AuthActionTokenRepository;
import uz.topdim.identity.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Бизнес-логика подтверждения email.
 *
 * <p>Флоу:
 * <ol>
 *   <li>requestEmailConfirmation(userId) — создаёт токен, отправляет на email</li>
 *   <li>confirmEmail(token) — проверяет токен, ставит emailVerified = true</li>
 * </ol>
 *
 * <p>Безопасность:
 * <ul>
 *   <li>Cooldown 60 секунд между повторными отправками</li>
 *   <li>Старые токены revoke'ятся при создании нового</li>
 *   <li>Идемпотентность: если email уже подтверждён — не спамим</li>
 *   <li>Мягкий подход: неподтверждённый email не блокирует login</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailConfirmationService {

    private final UserRepository userRepository;
    private final AuthActionTokenRepository actionTokenRepository;
    private final NotificationSender notificationSender;

    private static final int TOKEN_EXPIRATION_MINUTES = 60;
    private static final int COOLDOWN_SECONDS = 60;

    /**
     * Запрос отправки кода подтверждения email.
     * Идемпотентность: если email уже подтверждён — ничего не делаем.
     * Cooldown: повторный запрос в течение 60 секунд — ошибка.
     */
    @Transactional
    public void requestEmailConfirmation(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        if (user.isEmailVerified()) {
            throw new AuthException("Email уже подтверждён");
        }

        // Cooldown check
        List<AuthActionToken> activeTokens = actionTokenRepository
                .findActiveByUserIdAndType(userId, AuthActionType.EMAIL_CONFIRM);

        if (!activeTokens.isEmpty()) {
            AuthActionToken latest = activeTokens.get(0);
            if (latest.getLastSentAt() != null
                    && latest.getLastSentAt().plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
                throw new AuthException("Повторная отправка возможна через "
                        + COOLDOWN_SECONDS + " секунд");
            }
        }

        // Revoke старые токены
        actionTokenRepository.revokeAllActiveByUserIdAndType(userId, AuthActionType.EMAIL_CONFIRM);

        // Генерируем токен
        String plainToken = UUID.randomUUID().toString();
        String tokenHash = PasswordResetService.sha256(plainToken);

        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(user.getId())
                .type(AuthActionType.EMAIL_CONFIRM)
                .tokenHash(tokenHash)
                .target(user.getEmail())
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES))
                .lastSentAt(LocalDateTime.now())
                .build();

        actionTokenRepository.save(actionToken);

        // Отправляем
        notificationSender.sendEmailConfirmationToken(user.getEmail(), plainToken);

        log.info("SECURITY: Email confirmation requested for userId={}", userId);
    }

    /**
     * Подтверждение email по токену.
     * Проверяет: токен одноразовый, неистёкший, правильного типа.
     * После успеха: emailVerified = true, token помечается used.
     */
    @Transactional
    public void confirmEmail(String token) {
        String tokenHash = PasswordResetService.sha256(token);

        AuthActionToken actionToken = actionTokenRepository
                .findByTokenHashAndType(tokenHash, AuthActionType.EMAIL_CONFIRM)
                .orElseThrow(() -> new AuthException("Недействительный или просроченный токен подтверждения"));

        if (!actionToken.isValid()) {
            throw new AuthException("Недействительный или просроченный токен подтверждения");
        }

        User user = userRepository.findById(actionToken.getUserId())
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        // Идемпотентность: если уже подтверждён
        if (user.isEmailVerified()) {
            actionToken.setUsedAt(LocalDateTime.now());
            actionTokenRepository.save(actionToken);
            return;
        }

        user.setEmailVerified(true);
        userRepository.save(user);

        actionToken.setUsedAt(LocalDateTime.now());
        actionTokenRepository.save(actionToken);

        log.info("SECURITY: Email confirmed for userId={}", user.getId());
    }
}
