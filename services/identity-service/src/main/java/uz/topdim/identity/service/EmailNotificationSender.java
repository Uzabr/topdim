package uz.topdim.identity.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

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

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationSender(
            JavaMailSender mailSender,
            @Value("${notification.email.from:noreply@topdim.uz}") String fromAddress
    ) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        log.info("EmailNotificationSender activated — real emails will be sent from {}", fromAddress);
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

        sendEmail(target, subject, body);
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

        sendEmail(email, subject, body);
    }

    @Override
    public void sendPhoneConfirmationCode(String phone, String code) {
        // SMS пока не интегрирован — логируем
        log.info("NOTIFICATION [SMS STUB]: Phone confirmation code sent to {}: {}", maskTarget(phone), code);
    }

    private void sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} — {}", maskTarget(to), subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", maskTarget(to), e.getMessage(), e);
        }
    }

    private String maskTarget(String target) {
        if (target == null || target.length() < 4) return "***";
        return target.substring(0, 2) + "***" + target.substring(target.length() - 2);
    }
}
