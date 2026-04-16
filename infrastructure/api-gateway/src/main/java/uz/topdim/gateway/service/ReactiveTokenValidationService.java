package uz.topdim.gateway.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

/**
 * Реактивный сервис валидации состояния токена через Redis.
 *
 * <p>Проверяет две вещи:
 * <ol>
 *   <li>jti не в blacklist (token:blacklist:{jti}) — для logout</li>
 *   <li>securityVersion из JWT совпадает с текущим в Redis — для block/change-role/change-password</li>
 * </ol>
 *
 * <p>Если Redis недоступен — пропускаем (fail-open), чтобы не ломать весь сервис.
 * Это осознанный trade-off: при недоступности Redis заблокированные токены
 * продолжат работать до истечения TTL (обычно 15 мин).
 */
@Service
public class ReactiveTokenValidationService {

    private static final Logger log = LoggerFactory.getLogger(ReactiveTokenValidationService.class);

    private final ReactiveStringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "token:blacklist:";
    private static final String SECURITY_VERSION_PREFIX = "user:security-version:";

    public ReactiveTokenValidationService(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Проверяет, что jti не в blacklist.
     * @return true если токен заблокирован (должен быть отклонён)
     */
    public Mono<Boolean> isJtiBlacklisted(String jti) {
        if (jti == null) return Mono.just(false);

        return redisTemplate.hasKey(BLACKLIST_PREFIX + jti)
                .onErrorResume(e -> {
                    log.warn("Redis unavailable for jti blacklist check, fail-open: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Проверяет, что securityVersion из JWT актуален.
     * @return true если токен устарел (должен быть отклонён)
     */
    public Mono<Boolean> isSecurityVersionStale(String userId, Long tokenSecurityVersion) {
        if (userId == null || tokenSecurityVersion == null) return Mono.just(false);

        return redisTemplate.opsForValue().get(SECURITY_VERSION_PREFIX + userId)
                .map(redisVersion -> {
                    long currentVersion = Long.parseLong(redisVersion);
                    boolean stale = tokenSecurityVersion < currentVersion;
                    if (stale) {
                        log.info("SECURITY: Stale securityVersion for userId={}: token={}, current={}",
                                userId, tokenSecurityVersion, currentVersion);
                    }
                    return stale;
                })
                .defaultIfEmpty(false)
                .onErrorResume(e -> {
                    log.warn("Redis unavailable for securityVersion check, fail-open: {}", e.getMessage());
                    return Mono.just(false);
                });
    }

    /**
     * Комбинированная проверка: jti не в blacklist И securityVersion актуален.
     * @return true если токен НЕвалиден (должен быть отклонён)
     */
    public Mono<Boolean> isTokenInvalid(String jti, String userId, Long securityVersion) {
        Mono<Boolean> blacklisted = isJtiBlacklisted(jti);
        Mono<Boolean> stale = isSecurityVersionStale(userId, securityVersion);

        return Mono.zip(blacklisted, stale)
                .map(tuple -> tuple.getT1() || tuple.getT2());
    }
}
