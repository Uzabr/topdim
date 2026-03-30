package uz.topdim.auth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("Blacklist: добавляет токен с TTL в Redis")
    void blacklist_addsTokenWithTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.blacklist("jwt-token-123", 3600000L);

        verify(valueOperations).set(
                eq("token:blacklist:jwt-token-123"),
                eq("revoked"),
                eq(3600000L),
                eq(TimeUnit.MILLISECONDS)
        );
    }

    @Test
    @DisplayName("isBlacklisted: токен в blacklist → true")
    void isBlacklisted_tokenExists_returnsTrue() {
        when(redisTemplate.hasKey("token:blacklist:jwt-token-123")).thenReturn(true);

        boolean result = tokenBlacklistService.isBlacklisted("jwt-token-123");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isBlacklisted: токен не в blacklist → false")
    void isBlacklisted_tokenNotExists_returnsFalse() {
        when(redisTemplate.hasKey("token:blacklist:jwt-new-token")).thenReturn(false);

        boolean result = tokenBlacklistService.isBlacklisted("jwt-new-token");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isBlacklisted: Redis вернул null → false")
    void isBlacklisted_redisReturnsNull_returnsFalse() {
        when(redisTemplate.hasKey(anyString())).thenReturn(null);

        boolean result = tokenBlacklistService.isBlacklisted("jwt-token");

        assertThat(result).isFalse();
    }
}
