package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Сервис защиты от brute-force атак на login.
 * Использует Redis для хранения счётчика неудачных попыток per account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String ATTEMPTS_PREFIX = "auth:login:attempts:";
    private static final int MAX_ATTEMPTS_NO_DELAY = 3;
    private static final long TTL_MINUTES = 30;

    public void recordFailedAttempt(String email) {
        String key = ATTEMPTS_PREFIX + email.toLowerCase().trim();
        Long attempts = redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, TTL_MINUTES, TimeUnit.MINUTES);
        if (attempts != null) {
            if (attempts == 4) log.warn("SECURITY: Multiple failed login attempts ({}) for: {}", attempts, maskEmail(email));
            if (attempts == 8) log.warn("SECURITY: Excessive failed attempts ({}) for: {} — possible brute-force", attempts, maskEmail(email));
        }
    }

    public void resetAttempts(String email) {
        String key = ATTEMPTS_PREFIX + email.toLowerCase().trim();
        Long prev = getAttempts(email);
        redisTemplate.delete(key);
        if (prev != null && prev >= 4) log.info("SECURITY: Account unlocked after successful login: {}", maskEmail(email));
    }

    public Long getAttempts(String email) {
        String key = ATTEMPTS_PREFIX + email.toLowerCase().trim();
        String value = redisTemplate.opsForValue().get(key);
        return value != null ? Long.parseLong(value) : 0L;
    }

    public Duration getDelay(String email) {
        long attempts = getAttempts(email);
        if (attempts <= MAX_ATTEMPTS_NO_DELAY) return Duration.ZERO;
        else if (attempts <= 5) return Duration.ofSeconds(2);
        else if (attempts <= 7) return Duration.ofSeconds(5);
        else return Duration.ofSeconds(30);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) return "***" + email.substring(at);
        return email.charAt(0) + "***" + email.substring(at);
    }
}
