package uz.topdim.payment.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.common.events.OrderCreatedEvent;
import uz.topdim.payment.entity.Payment;
import uz.topdim.payment.entity.PaymentProvider;
import uz.topdim.payment.entity.PaymentStatus;
import uz.topdim.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderCreatedListenerTest {

    @Mock private PaymentService paymentService;

    @InjectMocks
    private OrderCreatedListener listener;

    @Test
    @DisplayName("Дублированный OrderCreatedEvent → не создаёт второй платёж")
    void duplicateOrderCreatedEvent_doesNotCreateSecondPayment() {
        Payment existing = Payment.builder()
                .id(1L).orderId(300L).userId(10L)
                .amount(BigDecimal.valueOf(50000)).status(PaymentStatus.PENDING)
                .provider(PaymentProvider.PAYME).currency("UZS").build();

        when(paymentService.findPaymentByOrderId(300L)).thenReturn(Optional.of(existing));

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(300L)
                .userId(10L)
                .totalAmount(BigDecimal.valueOf(50000))
                .build();

        listener.handleOrderCreated(event);

        verify(paymentService, never()).createPayment(anyLong(), anyLong(), any(BigDecimal.class), anyString());
    }

    @Test
    @DisplayName("Первый OrderCreatedEvent → создаёт платёж")
    void firstOrderCreatedEvent_createsPayment() {
        when(paymentService.findPaymentByOrderId(301L)).thenReturn(Optional.empty());

        Payment created = Payment.builder()
                .id(2L).orderId(301L).userId(11L)
                .amount(BigDecimal.valueOf(75000)).status(PaymentStatus.PENDING)
                .provider(PaymentProvider.PAYME).currency("UZS").build();
        when(paymentService.createPayment(eq(301L), eq(11L), eq(BigDecimal.valueOf(75000)), eq("PAYME")))
                .thenReturn(created);

        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(301L)
                .userId(11L)
                .totalAmount(BigDecimal.valueOf(75000))
                .build();

        listener.handleOrderCreated(event);

        verify(paymentService).createPayment(301L, 11L, BigDecimal.valueOf(75000), "PAYME");
    }
}
