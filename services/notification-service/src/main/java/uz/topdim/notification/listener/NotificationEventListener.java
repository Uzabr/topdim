package uz.topdim.notification.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.notification.service.NotificationService;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = "notification.queue")
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Received notification event for user: {}", event.getUserId());
        try {
            notificationService.createNotification(
                    event.getUserId(),
                    event.getTitle(),
                    event.getMessage(),
                    event.getType()
            );
        } catch (Exception e) {
            log.error("Failed to process notification event", e);
        }
    }
}
