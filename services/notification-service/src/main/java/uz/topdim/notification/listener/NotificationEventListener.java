package uz.topdim.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.notification.service.NotificationDeliveryService;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationDeliveryService deliveryService;

    @RabbitListener(queues = "notification.queue")
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Received notification event for userId={}", event.getUserId());
        deliveryService.handle(event);
    }
}
