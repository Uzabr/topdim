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
    private final String partnerBaseUrl;

    public EmailService(
            @Value("${notification.email.enabled:false}") boolean enabled,
            @Value("${notification.email.from:noreply@sizbiz.uz}") String fromAddress,
            @Value("${notification.telegram.partner-base-url:https://partner.sizbiz.uz}")
            String partnerBaseUrl,
            JavaMailSender mailSender
    ) {
        this.enabled = enabled;
        this.fromAddress = fromAddress;
        this.partnerBaseUrl = stripTrailingSlash(partnerBaseUrl);
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
        String subject = "sizbiz — Ваш купон: " + couponTitle;
        String body = String.format("""
                Здравствуйте!
                
                Вы успешно приобрели купон "%s".
                
                Код купона: %s
                
                Покажите этот код при визите к партнёру для получения скидки.
                
                Спасибо за покупку!
                Команда sizbiz
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
        String subject = "sizbiz — Заказ " + orderNumber + " подтверждён";
        String body = String.format("""
                Здравствуйте!
                
                Ваш заказ %s успешно оплачен.
                Купоны доступны в вашем профиле.
                
                Спасибо за покупку!
                Команда sizbiz
                """, orderNumber);

        sendEmail(to, subject, body);
    }

    public void sendProfileUpdateEmail(
            String to,
            String title,
            String message,
            String deepLink
    ) {
        if (!enabled) {
            throw new IllegalStateException("Email delivery is disabled");
        }
        String link = deepLink == null || deepLink.isBlank() ? "" : "\n\nОткрыть заявку: "
                + (deepLink.startsWith("http://") || deepLink.startsWith("https://")
                ? deepLink
                : partnerBaseUrl + (deepLink.startsWith("/") ? deepLink : "/" + deepLink));
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(fromAddress);
        mail.setTo(to);
        mail.setSubject("sizbiz — " + title);
        mail.setText(message + link);
        mailSender.send(mail);
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://partner.sizbiz.uz";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
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
