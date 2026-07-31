package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import uz.topdim.identity.security.SmsSender;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final StringRedisTemplate redis;
    private final SmsSender smsSender;
    private static final SecureRandom RND = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;

    public void requestOtp(String phone) {
        String code = String.format("%06d", RND.nextInt(1_000_000));
        redis.opsForValue().set("otp:" + phone, PasswordResetService.sha256(code), TTL);
        // Новый код → свежие попытки
        redis.delete("otp:attempts:" + phone);
        smsSender.sendOtp(phone, code);
    }

    public boolean verifyOtp(String phone, String code) {
        String stored = redis.opsForValue().get("otp:" + phone);
        if (stored == null) return false; // нет активного OTP: истёк/использован/залочен

        String attemptsRaw = redis.opsForValue().get("otp:attempts:" + phone);
        int attempts = attemptsRaw == null ? 0 : Integer.parseInt(attemptsRaw);
        if (attempts >= MAX_ATTEMPTS) {
            // Залочено: инвалидируем OTP даже если код случайно верный
            redis.delete("otp:" + phone);
            return false;
        }

        boolean ok = stored.equals(PasswordResetService.sha256(code));
        if (ok) {
            redis.delete("otp:" + phone);
            redis.delete("otp:attempts:" + phone);
            return true;
        }

        Long n = redis.opsForValue().increment("otp:attempts:" + phone);
        redis.expire("otp:attempts:" + phone, TTL.toMinutes(), TimeUnit.MINUTES);
        if (n != null && n >= MAX_ATTEMPTS) {
            redis.delete("otp:" + phone); // лок-аут: инвалидируем OTP досрочно
        }
        return false;
    }
}
