package uz.topdim.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.auth.entity.OutboxEvent;
import uz.topdim.auth.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Reconciliation job для outbox событий.
 * Находит застрявшие PENDING записи и сбрасывает их для повторной обработки.
 *
 * <p>Запускается каждые 15 минут.
 * Ищет PENDING записи старше 5 минут, у которых не превышен лимит попыток.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxReconciliationJob {

    private static final int MAX_ATTEMPTS = 10;
    private static final int STUCK_THRESHOLD_MINUTES = 5;

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Reconciliation: находит застрявшие события и помечает их для повторной обработки.
     * Выполняется каждые 15 минут.
     */
    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void reconcileStuckEvents() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<OutboxEvent> stuckEvents = outboxEventRepository.findStuckEvents(
                "PENDING", threshold, MAX_ATTEMPTS);

        if (stuckEvents.isEmpty()) {
            return;
        }

        log.warn("OUTBOX_RECONCILIATION: Found {} stuck PENDING events older than {} minutes",
                stuckEvents.size(), STUCK_THRESHOLD_MINUTES);

        for (OutboxEvent event : stuckEvents) {
            log.info("OUTBOX_RECONCILIATION: Resetting stuck event [eventId={}, createdAt={}, attempts={}, correlationId={}]",
                    event.getEventId(), event.getCreatedAt(), event.getAttemptCount(), event.getCorrelationId());
            // Оставляем статус PENDING — OutboxPublisher подберёт при следующем цикле.
            // Если event уже был attempt_count > 0, publisher попробует снова.
        }
    }
}
