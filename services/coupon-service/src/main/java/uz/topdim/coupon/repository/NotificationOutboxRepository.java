package uz.topdim.coupon.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.NotificationOutbox;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, Long> {

    boolean existsByEventKey(String eventKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select outbox
            from NotificationOutbox outbox
            where outbox.status = uz.topdim.coupon.entity.NotificationOutboxStatus.PENDING
              and outbox.nextAttemptAt <= :now
            order by outbox.id
            """)
    List<NotificationOutbox> findPublishable(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
