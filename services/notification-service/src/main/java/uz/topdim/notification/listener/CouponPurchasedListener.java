package uz.topdim.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.topdim.common.events.CouponPurchasedEvent;
import uz.topdim.notification.service.EmailService;
import uz.topdim.notification.service.SmsService;

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponPurchasedListener {

    private final EmailService emailService;
    private final SmsService smsService;

    @RabbitListener(queues = "coupon.purchased.queue")
    public void handleCouponPurchased(CouponPurchasedEvent event) {
        log.info("Coupon purchased: {} for user {} (code: {})",
                event.getCouponTitle(), event.getUserEmail(), event.getCouponCode());

        // Send email notification
        if (event.getUserEmail() != null) {
            emailService.sendCouponPurchasedEmail(
                    event.getUserEmail(),
                    event.getCouponTitle(),
                    event.getCouponCode()
            );
        }

        // Send SMS notification
        if (event.getUserPhone() != null) {
            smsService.sendCouponPurchasedSms(
                    event.getUserPhone(),
                    event.getCouponTitle(),
                    event.getCouponCode()
            );
        }
    }
}
