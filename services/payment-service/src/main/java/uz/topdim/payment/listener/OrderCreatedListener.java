package uz.topdim.payment.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.topdim.common.events.OrderCreatedEvent;
import uz.topdim.payment.entity.Payment;
import uz.topdim.payment.service.PaymentService;

/**
 * Слушатель OrderCreatedEvent из order-service.
 * При получении события автоматически создаёт Payment в статусе PENDING.
 * Это ключевая связка event-driven orchestration:
 * order-service → OrderCreatedEvent → payment-service → Payment(PENDING).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedListener {

    private final PaymentService paymentService;

    /**
     * Обрабатывает OrderCreatedEvent.
     * Создаёт Payment со статусом PENDING и провайдером PAYME по умолчанию.
     * Провайдер будет обновлён при реальном выборе пользователем.
     */
    @RabbitListener(queues = "order.created.payment.queue")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Получен OrderCreatedEvent: orderId={}, userId={}, amount={}",
                event.getOrderId(), event.getUserId(), event.getTotalAmount());

        try {
            // Проверяем, не создан ли уже платёж для этого заказа (idempotency)
            if (paymentService.findPaymentByOrderId(event.getOrderId()).isPresent()) {
                log.warn("Платёж для заказа {} уже существует, пропускаем", event.getOrderId());
                return;
            }

            Payment payment = paymentService.createPayment(
                    event.getOrderId(),
                    event.getUserId(),
                    event.getTotalAmount(),
                    "PAYME"  // default provider; будет обновлён при реальном выборе
            );

            log.info("Платёж {} создан автоматически для заказа {}, paymentUrl={}",
                    payment.getId(), event.getOrderId(), payment.getPaymentUrl());

        } catch (Exception e) {
            log.error("Ошибка создания платежа для заказа {}: {}",
                    event.getOrderId(), e.getMessage(), e);
            // В production здесь должен быть retry/DLQ механизм
        }
    }
}
