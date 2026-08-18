package uz.topdim.notification.listener;

import org.junit.jupiter.api.Test;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.notification.service.NotificationDeliveryService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class NotificationEventListenerFailureTest {

    @Test
    void unexpectedPersistenceFailurePropagatesSoRabbitCanRedeliver() {
        NotificationDeliveryService deliveryService = mock(NotificationDeliveryService.class);
        NotificationEvent event = NotificationEvent.builder()
                .eventKey("evt-db-failure")
                .userId(41L)
                .title("Status")
                .message("Message")
                .type("INFO")
                .build();
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(deliveryService).handle(event);
        NotificationEventListener listener = new NotificationEventListener(deliveryService);

        assertThatThrownBy(() -> listener.handleNotificationEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }
}
