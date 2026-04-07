package uz.topdim.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.auth.entity.OutboxEvent;
import uz.topdim.auth.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled publisher для outbox событий.
 * Периодически читает PENDING записи из outbox и отправляет в RabbitMQ.
 *
 * <p>Поведение при ошибках:
 * <ul>
 *   <li>Успех: status = PUBLISHED, published_at = now()</li>
 *   <li>Ошибка broker: attempt_count++, last_error записывается</li>
 *   <li>attempt_count > MAX_ATTEMPTS: status = FAILED (прекращает ретрай)</li>
 * </ul>
 */
@Slf4j
@Component
public class OutboxPublisher {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 10;
    private static final String EXCHANGE = "user.exchange";
    private static final String ROUTING_KEY = "user.registered";

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           RabbitTemplate rabbitTemplate,
                           MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.publishedCounter = Counter.builder("topdim.outbox.events.published")
                .description("Number of outbox events successfully published")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("topdim.outbox.events.failed")
                .description("Number of outbox event publish failures")
                .register(meterRegistry);
    }

    /**
     * Периодически забирает PENDING события из outbox и публикует в RabbitMQ.
     * Запуск: каждые 5 секунд (fixedDelay).
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> events = outboxEventRepository.findByStatusOrderByCreatedAtAsc(
                "PENDING", PageRequest.of(0, BATCH_SIZE));

        if (events.isEmpty()) {
            return;
        }

        log.debug("OUTBOX_PUBLISHER: Found {} pending events", events.size());

        for (OutboxEvent event : events) {
            publishEvent(event);
        }
    }

    /**
     * Публикует одно событие в RabbitMQ.
     * При успехе — помечает как PUBLISHED.
     * При ошибке — инкрементирует attempt_count, записывает last_error.
     * При превышении MAX_ATTEMPTS — помечает как FAILED.
     */
    private void publishEvent(OutboxEvent event) {
        try {
            rabbitTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, event.getPayload(), message -> {
                message.getMessageProperties().setHeader("eventId", event.getEventId());
                message.getMessageProperties().setHeader("eventType", event.getEventType());
                message.getMessageProperties().setHeader("correlationId", event.getCorrelationId());
                message.getMessageProperties().setContentType("application/json");
                return message;
            });

            event.setStatus("PUBLISHED");
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            publishedCounter.increment();

            log.info("OUTBOX_PUBLISHER: Event published [eventId={}, type={}, aggregateId={}, correlationId={}]",
                    event.getEventId(), event.getEventType(), event.getAggregateId(), event.getCorrelationId());

        } catch (Exception e) {
            event.setAttemptCount(event.getAttemptCount() + 1);
            event.setLastError(truncateError(e.getMessage()));

            if (event.getAttemptCount() >= MAX_ATTEMPTS) {
                event.setStatus("FAILED");
                log.error("OUTBOX_PUBLISHER: Event FAILED after {} attempts [eventId={}, correlationId={}]: {}",
                        event.getAttemptCount(), event.getEventId(), event.getCorrelationId(), e.getMessage());
            } else {
                log.warn("OUTBOX_PUBLISHER: Event publish failed, attempt {}/{} [eventId={}, correlationId={}]: {}",
                        event.getAttemptCount(), MAX_ATTEMPTS, event.getEventId(),
                        event.getCorrelationId(), e.getMessage());
            }

            outboxEventRepository.save(event);
            failedCounter.increment();
        }
    }

    private String truncateError(String error) {
        if (error == null) return "Unknown error";
        return error.length() > 500 ? error.substring(0, 500) : error;
    }
}
