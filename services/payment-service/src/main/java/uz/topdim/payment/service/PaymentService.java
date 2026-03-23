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
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final RabbitTemplate rabbitTemplate;

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

    @Transactional(readOnly = true)
    public Payment getPayment(Long id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден"));
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден для заказа " + orderId));
    }

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
}
