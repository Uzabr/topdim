package uz.topdim.coupon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.coupon.entity.NotificationOutbox;
import uz.topdim.coupon.entity.NotificationOutboxStatus;
import uz.topdim.coupon.repository.NotificationOutboxRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationOutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final long MAX_BACKOFF_MINUTES = 60L;

    private final NotificationOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelayString = "${app.notification-outbox.fixed-delay-ms:5000}")
    @Transactional
    public int publishPending() {
        var rows = repository.findPublishable(
                LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        int published = 0;
        for (NotificationOutbox row : rows) {
            if (publish(row)) {
                published++;
            }
        }
        return published;
    }

    private boolean publish(NotificationOutbox row) {
        try {
            NotificationEvent event = objectMapper.readValue(
                    row.getPayload(), NotificationEvent.class);
            rabbitTemplate.convertAndSend(
                    "notification.exchange", "notification.sent", event);
            row.setStatus(NotificationOutboxStatus.PUBLISHED);
            row.setPublishedAt(LocalDateTime.now());
            repository.save(row);
            return true;
        } catch (Exception exception) {
            int attemptCount = row.getAttemptCount() + 1;
            row.setAttemptCount(attemptCount);
            row.setStatus(NotificationOutboxStatus.PENDING);
            row.setNextAttemptAt(LocalDateTime.now().plusMinutes(backoffMinutes(attemptCount)));
            repository.save(row);
            log.warn("Notification outbox publish failed for eventKey={}, attempt={}",
                    row.getEventKey(), attemptCount);
            return false;
        }
    }

    private long backoffMinutes(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 6);
        return Math.min(1L << exponent, MAX_BACKOFF_MINUTES);
    }
}
