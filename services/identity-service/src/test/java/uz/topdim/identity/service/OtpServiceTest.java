package uz.topdim.identity.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import uz.topdim.identity.security.SmsSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock SmsSender sms;

    OtpService svc;

    @BeforeEach
    void init() {
        svc = new OtpService(redis, sms);
        when(redis.opsForValue()).thenReturn(ops);
    }

    @Test
    void request_generatesAndSends() {
        svc.requestOtp("+998901112233");

        verify(sms).sendOtp(eq("+998901112233"), anyString());
        verify(ops).set(startsWith("otp:"), anyString(), any());
    }

    @Test
    void verify_wrongCode_false() {
        when(ops.get("otp:+998901112233")).thenReturn(PasswordResetService.sha256("111111"));

        assertThat(svc.verifyOtp("+998901112233", "000000")).isFalse();
    }

    @Test
    void verify_correct_true_andClears() {
        when(ops.get("otp:+998901112233")).thenReturn(PasswordResetService.sha256("111111"));

        assertThat(svc.verifyOtp("+998901112233", "111111")).isTrue();
        verify(redis).delete("otp:+998901112233");
    }

    @Test
    void verify_wrongCode_doesNotDeleteKey() {
        when(ops.get("otp:+998901112233")).thenReturn(PasswordResetService.sha256("111111"));

        assertThat(svc.verifyOtp("+998901112233", "000000")).isFalse();
        verify(redis, never()).delete(anyString());
    }

    @Test
    void verify_missingKey_falseAndNoDelete() {
        when(ops.get("otp:+998901112233")).thenReturn(null);

        assertThat(svc.verifyOtp("+998901112233", "111111")).isFalse();
        verify(redis, never()).delete(anyString());
    }

    @Test
    void verify_incrementsAttemptsOnWrongCode() {
        when(ops.get("otp:+998901112233")).thenReturn(PasswordResetService.sha256("111111"));
        when(ops.increment("otp:attempts:+998901112233")).thenReturn(1L);

        assertThat(svc.verifyOtp("+998901112233", "000000")).isFalse();

        verify(ops).increment("otp:attempts:+998901112233");
    }

    @Test
    void verify_locksOutAfterMaxAttempts() {
        when(ops.get("otp:+998901112233")).thenReturn(PasswordResetService.sha256("111111"));
        when(ops.get("otp:attempts:+998901112233")).thenReturn("5");

        // Даже верный код не проходит — залочено после MAX попыток
        assertThat(svc.verifyOtp("+998901112233", "111111")).isFalse();

        verify(redis).delete("otp:+998901112233");
    }

    @Test
    void request_resetsAttempts() {
        svc.requestOtp("+998901112233");

        verify(redis).delete("otp:attempts:+998901112233");
    }
}
