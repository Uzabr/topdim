package uz.topdim.identity.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.common.events.PaymentCompletedEvent;
import uz.topdim.identity.service.UserService;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    UserService userService;

    @InjectMocks
    PaymentEventListener listener;

    @Test
    @DisplayName("handlePaymentCompleted: событие PaymentCompletedEvent → markPaid(userId)")
    void event_callsMarkPaid() {
        PaymentCompletedEvent e = PaymentCompletedEvent.builder().userId(42L).orderId(7L).build();

        listener.handlePaymentCompleted(e);

        verify(userService).markPaid(42L);
    }
}
