package uz.topdim.auth.service;

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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private LoginAttemptService loginAttemptService;

    private void setupValueOps() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== recordFailedAttempt ====================

    @Test
    @DisplayName("Неудачная попытка: увеличивает счётчик и ставит TTL")
    void recordFailedAttempt_incrementsAndSetsTtl() {
        setupValueOps();
        when(valueOperations.increment(anyString())).thenReturn(1L);

        loginAttemptService.recordFailedAttempt("user@test.com");

        verify(valueOperations).increment("auth:login:attempts:user@test.com");
        verify(redisTemplate).expire(eq("auth:login:attempts:user@test.com"), eq(30L), eq(TimeUnit.MINUTES));
    }

    @Test
    @DisplayName("Неудачная попытка: email нормализуется к lowercase")
    void recordFailedAttempt_normalizeEmail() {
        setupValueOps();
        when(valueOperations.increment(anyString())).thenReturn(1L);

        loginAttemptService.recordFailedAttempt("  User@TEST.COM  ");

        verify(valueOperations).increment("auth:login:attempts:user@test.com");
    }

    // ==================== resetAttempts ====================

    @Test
    @DisplayName("Сброс: удаляет ключ из Redis")
    void resetAttempts_deletesKey() {
        setupValueOps();
        when(valueOperations.get(anyString())).thenReturn("2");

        loginAttemptService.resetAttempts("user@test.com");

        verify(redisTemplate).delete("auth:login:attempts:user@test.com");
    }

    // ==================== getAttempts ====================

    @Test
    @DisplayName("Получение: ключ существует — возвращает число")
    void getAttempts_keyExists_returnsCount() {
        setupValueOps();
        when(valueOperations.get("auth:login:attempts:user@test.com")).thenReturn("5");

        Long result = loginAttemptService.getAttempts("user@test.com");

        assertThat(result).isEqualTo(5L);
    }

    @Test
    @DisplayName("Получение: ключ не существует — возвращает 0")
    void getAttempts_noKey_returnsZero() {
        setupValueOps();
        when(valueOperations.get(anyString())).thenReturn(null);

        Long result = loginAttemptService.getAttempts("new@test.com");

        assertThat(result).isEqualTo(0L);
    }

    // ==================== getDelay ====================

    @Test
    @DisplayName("Задержка: 1-3 попытки → 0 секунд")
    void getDelay_fewAttempts_noDelay() {
        setupValueOps();
        when(valueOperations.get(anyString())).thenReturn("2");

        Duration delay = loginAttemptService.getDelay("user@test.com");

        assertThat(delay).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("Задержка: 4-5 попыток → 2 секунды")
    void getDelay_mediumAttempts_2seconds() {
        setupValueOps();
        when(valueOperations.get(anyString())).thenReturn("4");

        Duration delay = loginAttemptService.getDelay("user@test.com");

        assertThat(delay).isEqualTo(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("Задержка: 6-7 попыток → 5 секунд")
    void getDelay_manyAttempts_5seconds() {
        setupValueOps();
        when(valueOperations.get(anyString())).thenReturn("7");

        Duration delay = loginAttemptService.getDelay("user@test.com");

        assertThat(delay).isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("Задержка: 8+ попыток → 30 секунд")
    void getDelay_excessiveAttempts_30seconds() {
        setupValueOps();
        when(valueOperations.get(anyString())).thenReturn("10");

        Duration delay = loginAttemptService.getDelay("user@test.com");

        assertThat(delay).isEqualTo(Duration.ofSeconds(30));
    }
}
