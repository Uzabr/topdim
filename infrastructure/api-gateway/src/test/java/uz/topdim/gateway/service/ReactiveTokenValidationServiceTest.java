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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReactiveTokenValidationServiceTest {

    @Mock private ReactiveStringRedisTemplate redisTemplate;
    @Mock private ReactiveValueOperations<String, String> valueOperations;

    @InjectMocks
    private ReactiveTokenValidationService tokenValidationService;

    @Test
    @DisplayName("isJtiBlacklisted: при ошибке Redis сервис работает fail-open")
    void isJtiBlacklisted_onRedisFailure_returnsFalse() {
        when(redisTemplate.hasKey("token:blacklist:jti-1"))
                .thenReturn(Mono.error(new RuntimeException("redis down")));

        Boolean result = tokenValidationService.isJtiBlacklisted("jti-1").block();

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isSecurityVersionStale: токен устарел, если версия в Redis выше")
    void isSecurityVersionStale_whenRedisVersionIsHigher_returnsTrue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:security-version:42")).thenReturn(Mono.just("5"));

        Boolean result = tokenValidationService.isSecurityVersionStale("42", 3L).block();

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isTokenInvalid: blacklisted или stale token отклоняется")
    void isTokenInvalid_whenAnyCheckFails_returnsTrue() {
        when(redisTemplate.hasKey("token:blacklist:jti-1")).thenReturn(Mono.just(false));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("user:security-version:42")).thenReturn(Mono.just("7"));

        Boolean result = tokenValidationService.isTokenInvalid("jti-1", "42", 6L).block();

        assertThat(result).isTrue();
    }
}
