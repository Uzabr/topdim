package uz.topdim.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Email notification service. Uses Spring Mail (SMTP).
 * For production, switch to SendGrid or similar.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String fromAddress;

    public EmailService(
            @Value("${notification.email.enabled:false}") boolean enabled,
            @Value("${notification.email.from:noreply@topdim.uz}") String fromAddress,
            JavaMailSender mailSender
    ) {
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.mailSender = mailSender;
    }

    /**
     * Отправляет email о покупке купона.
     * В stub mode — только логирует.
     *
     * @param to email получателя
     * @param couponTitle название купона
     * @param couponCode код купона
     */
    public void sendCouponPurchasedEmail(String to, String couponTitle, String couponCode) {
        String subject = "TopDim — Ваш купон: " + couponTitle;
        String body = String.format("""
                Здравствуйте!
                
                Вы успешно приобрели купон "%s".
                
                Код купона: %s
                
                Покажите этот код при визите к партнёру для получения скидки.
                
                Спасибо за покупку!
                Команда TopDim
                """, couponTitle, couponCode);

        sendEmail(to, subject, body);
    }

    /**
     * Отправляет email подтверждения заказа.
     *
     * @param to email получателя
     * @param orderNumber номер заказа
     */
    public void sendOrderConfirmationEmail(String to, String orderNumber) {
        String subject = "TopDim — Заказ " + orderNumber + " подтверждён";
        String body = String.format("""
                Здравствуйте!
                
                Ваш заказ %s успешно оплачен.
                Купоны доступны в вашем профиле.
                
                Спасибо за покупку!
                Команда TopDim
                """, orderNumber);

        sendEmail(to, subject, body);
    }

    private void sendEmail(String to, String subject, String body) {
        if (!enabled) {
            log.info("[EMAIL STUB] To: {}, Subject: {}", to, subject);
            log.debug("[EMAIL STUB] Body: {}", body);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent to {} — {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }
}
