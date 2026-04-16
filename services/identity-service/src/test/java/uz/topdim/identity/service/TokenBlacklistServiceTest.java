package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("blacklist: сохраняет jti с TTL в Redis")
    void blacklist_writesKeyWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.blacklist("jti-123", 15_000L);

        verify(valueOperations).set("token:blacklist:jti-123", "revoked", 15_000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("isBlacklisted: возвращает true, если ключ присутствует")
    void isBlacklisted_returnsRedisState() {
        when(redisTemplate.hasKey("token:blacklist:jti-123")).thenReturn(true);

        boolean result = tokenBlacklistService.isBlacklisted("jti-123");

        assertThat(result).isTrue();
    }
}
