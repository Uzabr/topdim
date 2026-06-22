package uz.topdim.gateway.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * M6: Тесты fail-closed/fail-open поведения ReactiveTokenValidationService
 * при недоступности Redis.
 */
@ExtendWith(MockitoExtension.class)
class ReactiveTokenValidationServiceTest {

    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOps;

    @InjectMocks
    private ReactiveTokenValidationService service;

    // --- isJtiBlacklisted ---

    @Test
    @DisplayName("M6: Redis error + privileged path → fail-CLOSED (token считается blacklisted)")
    void jtiBlacklist_redisError_privileged_failClosed() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        Boolean result = service.isJtiBlacklisted("jti-123", true).block();

        assertThat(result).isTrue(); // fail-closed: считаем заблокированным
    }

    @Test
    @DisplayName("M6: Redis error + обычный путь → fail-OPEN (token пропускается)")
    void jtiBlacklist_redisError_regular_failOpen() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        Boolean result = service.isJtiBlacklisted("jti-123", false).block();

        assertThat(result).isFalse(); // fail-open: пропускаем
    }

    @Test
    @DisplayName("M6: Redis OK + jti в blacklist → true (заблокирован)")
    void jtiBlacklist_redisOk_blacklisted() {
        when(redisTemplate.hasKey("token:blacklist:jti-123")).thenReturn(Mono.just(true));

        Boolean result = service.isJtiBlacklisted("jti-123", false).block();

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("M6: Redis OK + jti не в blacklist → false (не заблокирован)")
    void jtiBlacklist_redisOk_notBlacklisted() {
        when(redisTemplate.hasKey("token:blacklist:jti-456")).thenReturn(Mono.just(false));

        Boolean result = service.isJtiBlacklisted("jti-456", false).block();

        assertThat(result).isFalse();
    }

    // --- isSecurityVersionStale ---

    @Test
    @DisplayName("M6: Redis error + privileged path → fail-CLOSED (securityVersion считается stale)")
    void securityVersion_redisError_privileged_failClosed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        Boolean result = service.isSecurityVersionStale("user-1", 5L, true).block();

        assertThat(result).isTrue(); // fail-closed
    }

    @Test
    @DisplayName("M6: Redis error + обычный путь → fail-OPEN (securityVersion не считается stale)")
    void securityVersion_redisError_regular_failOpen() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        Boolean result = service.isSecurityVersionStale("user-1", 5L, false).block();

        assertThat(result).isFalse(); // fail-open
    }

    // --- isTokenInvalid (комбинированная) ---

    @Test
    @DisplayName("M6: Redis down + привилегированный путь → токен считается невалидным")
    void isTokenInvalid_redisDown_privileged_tokenInvalid() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        Boolean result = service.isTokenInvalid("jti-1", "user-1", 5L, true).block();

        assertThat(result).isTrue(); // fail-closed для admin/super
    }

    @Test
    @DisplayName("M6: Redis down + обычный путь → токен пропущен (fail-open)")
    void isTokenInvalid_redisDown_regular_tokenValid() {
        when(redisTemplate.hasKey(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.error(new RuntimeException("Redis down")));

        Boolean result = service.isTokenInvalid("jti-1", "user-1", 5L, false).block();

        assertThat(result).isFalse(); // fail-open для обычных путей
    }
}
