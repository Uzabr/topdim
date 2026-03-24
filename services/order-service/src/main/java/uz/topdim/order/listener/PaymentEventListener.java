package uz.topdim.order.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.topdim.common.events.PaymentCompletedEvent;
import uz.topdim.order.service.OrderService;

/**
 * Слушатель событий оплаты (RabbitMQ).
 * При PaymentCompleted → генерирует PurchasedCoupon[].
 * Публикует CouponPurchasedEvent для notification-service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final OrderService orderService;

    /**
     * Обработчик события PaymentCompleted (RabbitMQ).
     * При получении — генерирует PurchasedCoupon[] для заказа.
     *
     * @param event данные о завершённой оплате (orderId)
     */
    @RabbitListener(queues = "payment.completed.queue")
    public void handlePaymentCompleted(PaymentCompletedEvent event) {
        log.info("Payment completed for order {}: {}", event.getOrderId(), event.getTransactionId());
        try {
            orderService.generatePurchasedCoupons(event.getOrderId());
            log.info("Coupons generated for order {}", event.getOrderId());
        } catch (Exception e) {
            log.error("Error generating coupons for order {}: {}", event.getOrderId(), e.getMessage());
        }
    }
}
