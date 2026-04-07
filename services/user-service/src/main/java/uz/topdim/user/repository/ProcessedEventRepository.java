package uz.topdim.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.user.entity.ProcessedEvent;

/**
 * Репозиторий для обработанных событий (idempotency check).
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    /**
     * Проверяет, было ли событие уже обработано.
     */
    boolean existsByEventId(String eventId);
}
