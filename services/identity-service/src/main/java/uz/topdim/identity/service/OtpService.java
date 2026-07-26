package uz.topdim.identity.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import uz.topdim.identity.security.SmsSender;

import java.security.SecureRandom;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class OtpService {
    private final StringRedisTemplate redis;
    private final SmsSender smsSender;
    private static final SecureRandom RND = new SecureRandom();
    private static final Duration TTL = Duration.ofMinutes(5);

    public void requestOtp(String phone) {
        String code = String.format("%06d", RND.nextInt(1_000_000));
        redis.opsForValue().set("otp:" + phone, PasswordResetService.sha256(code), TTL);
        smsSender.sendOtp(phone, code);
    }

    public boolean verifyOtp(String phone, String code) {
        String stored = redis.opsForValue().get("otp:" + phone);
        if (stored == null) return false;
        boolean ok = stored.equals(PasswordResetService.sha256(code));
        if (ok) redis.delete("otp:" + phone);
        return ok;
    }
}
