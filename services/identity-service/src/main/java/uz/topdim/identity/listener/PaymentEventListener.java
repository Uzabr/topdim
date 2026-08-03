package uz.topdim.identity.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.topdim.common.events.PaymentCompletedEvent;
import uz.topdim.identity.service.UserService;

/**
 * Слушатель событий оплаты (RabbitMQ).
 * identity — ВТОРОЙ слушатель PaymentCompletedEvent (первый — order-service, генерация купонов).
 * При завершении оплаты фиксирует {@code paidAt} у пользователя (→ вклад в L1 через TrustService).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final UserService userService;

    @RabbitListener(queues = "payment.completed.identity.queue")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment completed → mark-paid user {} (order {})", event.getUserId(), event.getOrderId());
        try {
            userService.markPaid(event.getUserId());
        } catch (Exception e) {
            log.error("markPaid failed for user {}: {}", event.getUserId(), e.getMessage());
        }
    }
}
