package uz.topdim.identity.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LoggingSmsSender implements SmsSender {
    @Override
    public void sendOtp(String phone, String code) {
        log.info("[SMS STUB] OTP для {} = {}", phone, code); // Eskiz заменит в #3
    }
}
