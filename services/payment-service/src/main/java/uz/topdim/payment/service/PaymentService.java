package uz.topdim.payment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.PaymentCompletedEvent;
import uz.topdim.payment.entity.*;
import uz.topdim.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Сервис обработки платежей.
 * Создаёт Payment по OrderCreatedEvent.
 * Публикует PaymentCompleted/PaymentFailed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * Создаёт новый платёж (статус: PENDING).
     * Вызывается при получении OrderCreatedEvent.
     *
     * @param orderId ID заказа
     * @param userId ID пользователя
     * @param amount сумма платежа
     * @param provider платёжный провайдер (PAYME, CLICK, UZUM)
     * @return созданный платёж
     */
    @Transactional
    public Payment createPayment(Long orderId, Long userId, BigDecimal amount, String provider) {
        PaymentProvider paymentProvider;
        try {
            paymentProvider = PaymentProvider.valueOf(provider.toUpperCase());
        } catch (IllegalArgumentException e) {
            paymentProvider = PaymentProvider.PAYME;
        }

        String paymentId = UUID.randomUUID().toString();

        Payment payment = Payment.builder()
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .currency("UZS")
                .provider(paymentProvider)
                .status(PaymentStatus.PENDING)
                .transactionId(paymentId)
                .paymentUrl("https://payment.topdim.uz/pay/" + paymentId)
                .build();

        payment = paymentRepository.save(payment);
        log.info("Создан платёж {} для заказа {}", payment.getId(), orderId);
        return payment;
    }

    /**
     * Получает платёж по ID.
     *
     * @param id ID платежа
     * @return платёж
     * @throws RuntimeException если не найден
     */
    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден"));
    }

    /**
     * Получает платёж по ID.
     *
     * @param id ID платежа
     * @return платёж
     * @throws RuntimeException если не найден
     */
    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден для заказа " + orderId));
    }

    /**
     * Находит платёж по orderId (Optional).
     * Используется для storefront polling — не бросает исключение если payment ещё не создан.
     *
     * @param orderId ID заказа
     * @return Optional<Payment>
     */
    @Transactional(readOnly = true)
    public Optional<Payment> findPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId);
    }

    /**
     * Обрабатывает callback от платёжной системы.
     * Обновляет статус на COMPLETED и публикует PaymentCompletedEvent.
     *
     * @param orderId ID заказа
     * @param userId ID пользователя
     * @param amount сумма
     * @param provider провайдер
     * @param transactionId ID транзакции от провайдера
     * @return обновлённый платёж
     */
    @Transactional
    public Payment handleCallback(Long orderId, Long userId, BigDecimal amount, String provider, String transactionId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseGet(() -> Payment.builder()
                        .orderId(orderId)
                        .userId(userId)
                        .amount(amount)
                        .currency("UZS")
                        .provider(PaymentProvider.PAYME)
                        .status(PaymentStatus.PENDING)
                        .build());

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());
        if (transactionId != null) {
            payment.setTransactionId(transactionId);
        }
        payment = paymentRepository.save(payment);

        // Publish event
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .userId(userId)
                .amount(amount)
                .paymentProvider(provider != null ? provider : "payme")
                .transactionId(payment.getTransactionId())
                .completedAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend("payment.exchange", "payment.completed", event);

        log.info("Платёж {} завершён для заказа {}", payment.getId(), orderId);
        return payment;
    }

    /**
     * Завершает платёж в demo-режиме без реального провайдера.
     * Переиспользует ту же цепочку: Payment(COMPLETED) → PaymentCompletedEvent.
     * Идемпотентен: если платёж уже COMPLETED — возвращает его без повторной публикации события.
     *
     * @param orderId ID заказа
     * @return обновлённый платёж
     * @throws IllegalArgumentException если платёж не найден
     * @throws IllegalStateException если платёж в статусе FAILED/REFUNDED
     */
    @Transactional
    public Payment demoComplete(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден для заказа " + orderId));

        // Идемпотентность: если уже COMPLETED — вернуть без повторной публикации
        if (payment.getStatus() == PaymentStatus.COMPLETED) {
            log.info("Платёж для заказа {} уже завершён, пропускаем demo-completion", orderId);
            return payment;
        }

        // Нельзя завершить FAILED/REFUNDED платёж
        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException(
                    "Невозможно завершить платёж в статусе " + payment.getStatus() + " для заказа " + orderId);
        }

        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setCompletedAt(LocalDateTime.now());
        payment.setTransactionId("DEMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        payment = paymentRepository.save(payment);

        // Публикуем то же событие, что и при реальном callback
        PaymentCompletedEvent event = PaymentCompletedEvent.builder()
                .paymentId(payment.getId())
                .orderId(orderId)
                .userId(payment.getUserId())
                .amount(payment.getAmount())
                .paymentProvider("DEMO")
                .transactionId(payment.getTransactionId())
                .completedAt(LocalDateTime.now())
                .build();
        rabbitTemplate.convertAndSend("payment.exchange", "payment.completed", event);

        log.info("Demo-платёж {} завершён для заказа {}", payment.getId(), orderId);
        return payment;
    }
}
