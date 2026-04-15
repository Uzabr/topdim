package uz.topdim.identity.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityVersionServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private SecurityVersionService securityVersionService;

    @Test
    @DisplayName("publishSecurityVersion: записывает текущую версию в Redis по userId")
    void publishSecurityVersion_writesRedisKey() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        securityVersionService.publishSecurityVersion(42L, 7L);

        verify(valueOperations).set("user:security-version:42", "7");
    }

    @Test
    @DisplayName("removeSecurityVersion: удаляет ключ пользователя из Redis")
    void removeSecurityVersion_deletesRedisKey() {
        securityVersionService.removeSecurityVersion(42L);

        verify(redisTemplate).delete("user:security-version:42");
    }
}
