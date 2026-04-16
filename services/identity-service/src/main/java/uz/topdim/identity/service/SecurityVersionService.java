package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Сервис для управления securityVersion в Redis.
 * Gateway читает securityVersion из Redis (по userId) и сравнивает с JWT claim.
 * Если значение в Redis больше, чем в JWT — токен невалиден (force re-login).
 *
 * <p>Ключ: user:security-version:{userId}
 * <p>Значение: текущий securityVersion (long)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityVersionService {

    private final RedisTemplate<String, String> redisTemplate;
    private static final String KEY_PREFIX = "user:security-version:";

    /**
     * Публикует текущий securityVersion пользователя в Redis.
     * Вызывается при: change-password, block, unblock, change-role.
     */
    public void publishSecurityVersion(Long userId, long securityVersion) {
        String key = KEY_PREFIX + userId;
        redisTemplate.opsForValue().set(key, String.valueOf(securityVersion));
        log.info("SECURITY: Published securityVersion={} for userId={}", securityVersion, userId);
    }

    /**
     * Удаляет securityVersion из Redis (при удалении пользователя).
     */
    public void removeSecurityVersion(Long userId) {
        String key = KEY_PREFIX + userId;
        redisTemplate.delete(key);
    }
}
