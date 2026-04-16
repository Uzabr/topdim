package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    private void setupValueOperations() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("recordFailedAttempt: нормализует email и продлевает TTL")
    void recordFailedAttempt_normalizesEmailAndSetsTtl() {
        setupValueOperations();
        when(valueOperations.increment(anyString())).thenReturn(1L);

        loginAttemptService.recordFailedAttempt("  USER@Test.COM ");

        verify(valueOperations).increment("auth:login:attempts:user@test.com");
        verify(redisTemplate).expire(eq("auth:login:attempts:user@test.com"), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("getDelay: после 8 и более попыток даёт 30 секунд")
    void getDelay_excessiveAttempts_returnsThirtySeconds() {
        setupValueOperations();
        when(valueOperations.get("auth:login:attempts:user@test.com")).thenReturn("8");

        Duration delay = loginAttemptService.getDelay("user@test.com");

        assertThat(delay).isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("resetAttempts: удаляет накопленные попытки")
    void resetAttempts_deletesRedisKey() {
        setupValueOperations();
        when(valueOperations.get("auth:login:attempts:user@test.com")).thenReturn("4");

        loginAttemptService.resetAttempts("user@test.com");

        verify(redisTemplate).delete("auth:login:attempts:user@test.com");
    }
}
