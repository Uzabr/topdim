package uz.topdim.payment.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uz.topdim.payment.entity.*;
import uz.topdim.payment.repository.PaymentRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PaymentService paymentService;

    @Test
    @DisplayName("Создание платежа: статус PENDING, генерирует URL")
    void createPayment_statusPending() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(1L);
            return p;
        });

        Payment result = paymentService.createPayment(100L, 10L, BigDecimal.valueOf(150000), "payme");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.getProvider()).isEqualTo(PaymentProvider.PAYME);
        assertThat(result.getOrderId()).isEqualTo(100L);
        assertThat(result.getPaymentUrl()).startsWith("https://payment.topdim.uz/pay/");
        assertThat(result.getTransactionId()).isNotBlank();
    }

    @Test
    @DisplayName("Создание платежа: невалидный провайдер → дефолт PAYME")
    void createPayment_invalidProvider_defaultsToPayme() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> {
            Payment p = inv.getArgument(0);
            p.setId(2L);
            return p;
        });

        Payment result = paymentService.createPayment(101L, 10L, BigDecimal.valueOf(50000), "stripe");

        assertThat(result.getProvider()).isEqualTo(PaymentProvider.PAYME);
    }

    @Test
    @DisplayName("Callback: завершает платёж → COMPLETED + публикует событие")
    void handleCallback_completesPaymentAndPublishesEvent() {
        Payment existing = Payment.builder()
                .id(1L).orderId(100L).userId(10L)
                .amount(BigDecimal.valueOf(150000)).status(PaymentStatus.PENDING)
                .provider(PaymentProvider.PAYME).currency("UZS").build();

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(existing));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.handleCallback(100L, 10L, BigDecimal.valueOf(150000), "payme", "txn-123");

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
        assertThat(result.getTransactionId()).isEqualTo("txn-123");
        verify(rabbitTemplate).convertAndSend(eq("payment.exchange"), eq("payment.completed"), any(Object.class));
    }

    @Test
    @DisplayName("Получение платежа: не найден → IllegalArgumentException")
    void getPayment_notFound_throwsException() {
        when(paymentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }

    @Test
    @DisplayName("Получение по orderId: не найден → IllegalArgumentException")
    void getPaymentByOrderId_notFound_throwsException() {
        when(paymentRepository.findByOrderId(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPaymentByOrderId(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("не найден");
    }
}
