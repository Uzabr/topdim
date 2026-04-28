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
import java.time.LocalDateTime;
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

    // ─── Existing tests ───

    @Test
    @DisplayName("Создание платежа: статус PENDING, генерирует URL")
    void createPayment_statusPending() {
        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.empty());
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
        when(paymentRepository.findByOrderId(101L)).thenReturn(Optional.empty());
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

    // ─── Task 1: Idempotency tests ───

    @Test
    @DisplayName("createPayment: если платёж для orderId уже существует → возвращает существующий, не создаёт новый")
    void createPayment_existingOrder_returnsExistingPaymentAndDoesNotSaveNewOne() {
        Payment existing = Payment.builder()
                .id(5L).orderId(200L).userId(10L)
                .amount(BigDecimal.valueOf(100000)).status(PaymentStatus.PENDING)
                .provider(PaymentProvider.PAYME).currency("UZS")
                .transactionId("existing-txn").paymentUrl("https://payment.topdim.uz/pay/existing-txn")
                .build();

        when(paymentRepository.findByOrderId(200L)).thenReturn(Optional.of(existing));

        Payment result = paymentService.createPayment(200L, 10L, BigDecimal.valueOf(100000), "payme");

        // Must return existing payment, not save a new one
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTransactionId()).isEqualTo("existing-txn");
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("handleCallback: уже COMPLETED платёж → не публикует повторный PaymentCompletedEvent")
    void handleCallback_completedPayment_doesNotRepublishPaymentCompletedEvent() {
        Payment alreadyCompleted = Payment.builder()
                .id(1L).orderId(100L).userId(10L)
                .amount(BigDecimal.valueOf(150000)).status(PaymentStatus.COMPLETED)
                .provider(PaymentProvider.PAYME).currency("UZS")
                .completedAt(LocalDateTime.now().minusMinutes(5))
                .transactionId("txn-already-done")
                .build();

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(alreadyCompleted));

        Payment result = paymentService.handleCallback(100L, 10L, BigDecimal.valueOf(150000), "payme", "txn-retry");

        // Must return existing completed payment without re-publishing event
        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getTransactionId()).isEqualTo("txn-already-done"); // original txn preserved
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    // ─── Task 3: demoComplete tests ───

    @Test
    @DisplayName("demoComplete: pending payment -> COMPLETED and publishes PaymentCompletedEvent")
    void demoComplete_pendingPayment_completesAndPublishesEvent() {
        Payment pending = Payment.builder()
                .id(1L)
                .orderId(100L)
                .userId(10L)
                .amount(BigDecimal.valueOf(150000))
                .currency("UZS")
                .provider(PaymentProvider.PAYME)
                .status(PaymentStatus.PENDING)
                .build();

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(pending));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        Payment result = paymentService.demoComplete(100L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(result.getCompletedAt()).isNotNull();
        assertThat(result.getTransactionId()).startsWith("DEMO-");
        verify(rabbitTemplate).convertAndSend(eq("payment.exchange"), eq("payment.completed"), any(Object.class));
    }

    @Test
    @DisplayName("demoComplete: already COMPLETED payment -> returns existing without republishing")
    void demoComplete_completedPayment_doesNotRepublishEvent() {
        Payment completed = Payment.builder()
                .id(1L)
                .orderId(100L)
                .userId(10L)
                .amount(BigDecimal.valueOf(150000))
                .currency("UZS")
                .provider(PaymentProvider.PAYME)
                .status(PaymentStatus.COMPLETED)
                .transactionId("DEMO-OLD123")
                .completedAt(LocalDateTime.now().minusMinutes(5))
                .build();

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(completed));

        Payment result = paymentService.demoComplete(100L);

        assertThat(result).isSameAs(completed);
        assertThat(result.getTransactionId()).isEqualTo("DEMO-OLD123");
        verify(paymentRepository, never()).save(any(Payment.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }

    @Test
    @DisplayName("demoComplete: failed payment -> business error and no event")
    void demoComplete_failedPayment_throwsWithoutPublishingEvent() {
        Payment failed = Payment.builder()
                .id(1L)
                .orderId(100L)
                .userId(10L)
                .amount(BigDecimal.valueOf(150000))
                .currency("UZS")
                .provider(PaymentProvider.PAYME)
                .status(PaymentStatus.FAILED)
                .build();

        when(paymentRepository.findByOrderId(100L)).thenReturn(Optional.of(failed));

        assertThatThrownBy(() -> paymentService.demoComplete(100L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Невозможно завершить платёж");

        verify(paymentRepository, never()).save(any(Payment.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class));
    }
}

