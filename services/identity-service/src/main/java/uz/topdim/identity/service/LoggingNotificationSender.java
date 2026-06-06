package uz.topdim.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Заглушка NotificationSender — логирует отправку, не делает реальных вызовов.
 * Активируется когда notification.email.enabled != true (по умолчанию для dev/test).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingNotificationSender implements NotificationSender {

    public LoggingNotificationSender() {
        log.warn("LoggingNotificationSender activated — emails will NOT be sent, only logged");
    }

    @Override
    public void sendPasswordResetToken(String target, String token) {
        log.info("NOTIFICATION [STUB]: Password reset token sent to {}: {}", maskTarget(target), token);
    }

    @Override
    public void sendEmailConfirmationToken(String email, String token) {
        log.info("NOTIFICATION [STUB]: Email confirmation token sent to {}: {}", maskTarget(email), token);
    }

    @Override
    public void sendPhoneConfirmationCode(String phone, String code) {
        log.info("NOTIFICATION [STUB]: Phone confirmation code sent to {}: {}", maskTarget(phone), code);
    }

    private String maskTarget(String target) {
        if (target == null || target.length() < 4) return "***";
        return target.substring(0, 2) + "***" + target.substring(target.length() - 2);
    }
}
