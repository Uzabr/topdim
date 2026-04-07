package uz.topdim.auth.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.auth.entity.OutboxEvent;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для outbox событий.
 * Используется OutboxPublisher для получения и обновления PENDING записей.
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Получает PENDING записи для публикации (ordered by created_at ASC).
     */
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    /**
     * Получает застрявшие PENDING записи старше указанной даты (для reconciliation).
     * Исключает записи, у которых превышено максимальное количество попыток.
     */
    @Query("SELECT o FROM OutboxEvent o WHERE o.status = :status AND o.createdAt < :before AND o.attemptCount < :maxAttempts")
    List<OutboxEvent> findStuckEvents(
            @Param("status") String status,
            @Param("before") LocalDateTime before,
            @Param("maxAttempts") int maxAttempts
    );
}
