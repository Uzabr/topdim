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
 * Бизнес-логика смены email с подтверждением владения новым адресом.
 *
 * <p>Критично для Telegram/телефон-юзеров с placeholder-email
 * ({@code tg_*@topdim.uz}, {@code phone_*@topdim.uz}, {@code released_*@topdim.uz}) —
 * без этого флоу они не могут задать реальный адрес.
 *
 * <p>Флоу:
 * <ol>
 *   <li>requestEmailChange(userId, newEmail) — создаёт токен, отправляет его на НОВЫЙ адрес
 *       (proof-of-ownership — доказательство владения новым адресом, не старым)</li>
 *   <li>confirmEmailChange(token) — проверяет токен, меняет email пользователя, ставит emailVerified = true</li>
 * </ol>
 *
 * <p>Безопасность:
 * <ul>
 *   <li>Cooldown 60 секунд между повторными запросами</li>
 *   <li>Старые неиспользованные EMAIL_CHANGE-токены revoke'ятся при создании нового</li>
 *   <li>Пользователь авторизован (не anti-enumeration сценарий) — «email занят» это явный
 *       отказ {@link IllegalStateException} (маппится в 409 CONFLICT), а не молчаливое 202</li>
 *   <li>Занятость нового адреса проверяется дважды: при request и повторно при confirm (гонка)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailChangeService {

    private final UserRepository userRepository;
    private final AuthActionTokenRepository actionTokenRepository;
    private final NotificationSender notificationSender;

    private static final int TOKEN_EXPIRATION_MINUTES = 60;
    private static final int COOLDOWN_SECONDS = 60;

    /**
     * Запрос смены email. Требует, чтобы пользователь был аутентифицирован (userId из gateway).
     * Новый адрес не должен быть занят другим пользователем.
     * Cooldown: повторный запрос в течение 60 секунд — ошибка.
     */
    @Transactional
    public void requestEmailChange(Long userId, String newEmail) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        String normalizedEmail = newEmail.toLowerCase().trim();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new IllegalStateException("Email уже используется");
        }

        // Cooldown check
        List<AuthActionToken> activeTokens = actionTokenRepository
                .findActiveByUserIdAndType(userId, AuthActionType.EMAIL_CHANGE);

        if (!activeTokens.isEmpty()) {
            AuthActionToken latest = activeTokens.get(0);
            if (latest.getLastSentAt() != null
                    && latest.getLastSentAt().plusSeconds(COOLDOWN_SECONDS).isAfter(LocalDateTime.now())) {
                throw new AuthException("Повторная отправка возможна через "
                        + COOLDOWN_SECONDS + " секунд");
            }
        }

        // Revoke старые токены
        actionTokenRepository.revokeAllActiveByUserIdAndType(userId, AuthActionType.EMAIL_CHANGE);

        // Генерируем токен
        String plainToken = UUID.randomUUID().toString();
        String tokenHash = PasswordResetService.sha256(plainToken);

        AuthActionToken actionToken = AuthActionToken.builder()
                .userId(user.getId())
                .type(AuthActionType.EMAIL_CHANGE)
                .tokenHash(tokenHash)
                .target(normalizedEmail)
                .expiresAt(LocalDateTime.now().plusMinutes(TOKEN_EXPIRATION_MINUTES))
                .lastSentAt(LocalDateTime.now())
                .build();

        actionTokenRepository.save(actionToken);

        // Отправляем на НОВЫЙ адрес — доказательство владения новым адресом, не старым
        notificationSender.sendEmailChangeToken(normalizedEmail, plainToken);

        log.info("SECURITY: Email change requested for userId={}", userId);
    }

    /**
     * Подтверждение смены email по токену.
     * Проверяет: токен одноразовый, неистёкший, правильного типа; повторно проверяет,
     * что целевой адрес всё ещё не занят (гонка между request и confirm).
     * После успеха: email пользователя обновлён, emailVerified = true, token помечается used.
     */
    @Transactional
    public void confirmEmailChange(String token) {
        String tokenHash = PasswordResetService.sha256(token);

        AuthActionToken actionToken = actionTokenRepository
                .findByTokenHashAndType(tokenHash, AuthActionType.EMAIL_CHANGE)
                .orElseThrow(() -> new AuthException("Недействительный или просроченный токен смены email"));

        if (!actionToken.isValid()) {
            throw new AuthException("Недействительный или просроченный токен смены email");
        }

        String newEmail = actionToken.getTarget();

        // Повторная проверка на гонку: адрес мог быть занят другим пользователем между request и confirm
        if (userRepository.existsByEmailIgnoreCase(newEmail)) {
            throw new IllegalStateException("Email уже используется");
        }

        User user = userRepository.findById(actionToken.getUserId())
                .orElseThrow(() -> new AuthException("Пользователь не найден"));

        user.setEmail(newEmail);
        user.setEmailVerified(true);
        userRepository.save(user);

        actionToken.setUsedAt(LocalDateTime.now());
        actionTokenRepository.save(actionToken);

        log.info("SECURITY: Email changed for userId={}", user.getId());
    }
}
