package uz.topdim.identity.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Реальная реализация NotificationSender через SMTP (Gmail).
 * Активируется когда notification.email.enabled=true.
 * Для SMS (телефон) пока логируем — интеграция с Eskiz.uz будет позже.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "notification.email.enabled", havingValue = "true")
public class EmailNotificationSender implements NotificationSender {

    private static final String METRIC_EMAIL_FAILED = "notification.email.failed";
    private static final String TAG_TYPE = "type";
    private static final String TYPE_RESET = "reset";
    private static final String TYPE_CONFIRM = "confirm";

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final MeterRegistry meterRegistry;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${notification.email.from:noreply@topdim.uz}") String fromAddress,
            MeterRegistry meterRegistry,
            @Value("${spring.mail.username:}") String mailUsername,
            @Value("${spring.mail.password:}") String mailPassword
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.meterRegistry = meterRegistry;
        log.info("EmailNotificationSender activated — real emails will be sent from {}", fromAddress);
        if (!StringUtils.hasText(mailUsername) || !StringUtils.hasText(mailPassword)) {
            log.warn("notification.email.enabled=true, но SMTP-креды (MAIL_USERNAME/MAIL_PASSWORD) не заданы " +
                    "— письма не будут доставляться");
        }
    }

    @Override
    public void sendPasswordResetToken(String target, String token) {
        String subject = "TopDim — Сброс пароля";
        String body = String.format("""
                Здравствуйте!
                
                Вы запросили сброс пароля на TopDim.
                
                Ваш код для сброса пароля: %s
                
                Код действителен в течение 15 минут.
                Если вы не запрашивали сброс пароля — проигнорируйте это письмо.
                
                Команда TopDim
                """, token);

        sendEmail(target, subject, body, TYPE_RESET);
    }

    @Override
    public void sendEmailConfirmationToken(String email, String token) {
        String subject = "TopDim — Подтверждение email";
        String body = String.format("""
                Здравствуйте!
                
                Для подтверждения вашего email на TopDim используйте код:
                
                %s
                
                Код действителен в течение 60 минут.
                
                Команда TopDim
                """, token);

        sendEmail(email, subject, body, TYPE_CONFIRM);
    }

    @Override
    public void sendPhoneConfirmationCode(String phone, String code) {
        // SMS пока не интегрирован — логируем
        log.info("NOTIFICATION [SMS STUB]: Phone confirmation code sent to {}: {}", maskTarget(phone), code);
    }

    private void sendEmail(String to, String subject, String body, String type) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} — {}", maskTarget(to), subject);
        } catch (Exception e) {
            meterRegistry.counter(METRIC_EMAIL_FAILED, TAG_TYPE, type).increment();
            log.error("Failed to send email to {}: {}", maskTarget(to), e.getMessage(), e);
        }
    }

    private String maskTarget(String target) {
        if (target == null || target.length() < 4) return "***";
        return target.substring(0, 2) + "***" + target.substring(target.length() - 2);
    }
}
