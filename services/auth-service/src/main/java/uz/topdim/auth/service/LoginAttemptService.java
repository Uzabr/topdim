package uz.topdim.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Сервис защиты от brute-force атак на login.
 * Использует Redis для хранения счётчика неудачных попыток per account.
 *
 * <p>Progressive delay вместо жёсткого lockout (OWASP):
 * <ul>
 *   <li>1-3 попытки: без задержки</li>
 *   <li>4-5 попытки: задержка 2 секунды</li>
 *   <li>6-7 попытки: задержка 5 секунд</li>
 *   <li>8+ попытки: задержка 30 секунд</li>
 * </ul>
 *
 * <p>Ответ всегда generic: "Неверный email или пароль" (для wrong password,
 * user not found, locked account — одинаковый ответ).
 *
 * <p>Счётчик автоматически сбрасывается:
 * <ul>
 *   <li>После успешного логина</li>
 *   <li>Через 30 минут неактивности (TTL в Redis)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String ATTEMPTS_PREFIX = "auth:login:attempts:";
    private static final int MAX_ATTEMPTS_NO_DELAY = 3;
    private static final long TTL_MINUTES = 30;

    /**
     * Регистрирует неудачную попытку входа.
     * Увеличивает счётчик и логирует события безопасности.
     *
     * @param email email аккаунта (нормализованный к lowercase)
     */
    public void recordFailedAttempt(String email) {
        String key = ATTEMPTS_PREFIX + email.toLowerCase().trim();
        Long attempts = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);

        if (attempts != null) {
            if (attempts == 4) {
                log.warn("SECURITY: Multiple failed login attempts ({}) for account: {}",
                        attempts, maskEmail(email));
            }
            if (attempts == 8) {
                log.warn("SECURITY: Excessive failed login attempts ({}) for account: {} — possible brute-force",
                        attempts, maskEmail(email));
            }
        }
    }

    /**
     * Сбрасывает счётчик после успешного логина.
     *
     * @param email email аккаунта
     */
    public void resetAttempts(String email) {
        String key = ATTEMPTS_PREFIX + email.toLowerCase().trim();
        Long previousAttempts = getAttempts(email);
        redisTemplate.delete(key);

        if (previousAttempts != null && previousAttempts >= 4) {
            log.info("SECURITY: Account unlocked after successful login: {}", maskEmail(email));
        }
    }

    /**
     * Возвращает количество неудачных попыток.
     */
    public Long getAttempts(String email) {
        String key = ATTEMPTS_PREFIX + email.toLowerCase().trim();
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    /**
     * Вычисляет задержку (progressive delay) на основе количества попыток.
     * Вместо жёсткой блокировки — увеличивающаяся задержка.
     *
     * @param email email аккаунта
     * @return Duration задержки перед ответом
     */
    public Duration getDelay(String email) {
        long attempts = getAttempts(email);

        if (attempts <= MAX_ATTEMPTS_NO_DELAY) {
            return Duration.ZERO;
        } else if (attempts <= 5) {
            return Duration.ofSeconds(2);
        } else if (attempts <= 7) {
            return Duration.ofSeconds(5);
        } else {
            return Duration.ofSeconds(30);
        }
    }

    /**
     * Маскирует email для логов (GDPR/безопасность).
     * user@example.com → u***@example.com
     */
    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
