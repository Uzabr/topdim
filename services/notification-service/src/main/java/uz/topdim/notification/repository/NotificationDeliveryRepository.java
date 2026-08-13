package uz.topdim.notification.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.notification.entity.NotificationDelivery;
import uz.topdim.notification.entity.NotificationDeliveryChannel;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository
        extends JpaRepository<NotificationDelivery, Long> {

    Optional<NotificationDelivery> findByEventKeyAndUserIdAndChannel(
            String eventKey,
            Long userId,
            NotificationDeliveryChannel channel
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select delivery
            from NotificationDelivery delivery
            where delivery.status = uz.topdim.notification.entity.NotificationDeliveryStatus.RETRY
              and delivery.nextAttemptAt <= :now
            order by delivery.id
            """)
    List<NotificationDelivery> findRetryable(
            @Param("now") LocalDateTime now,
            Pageable pageable
    );
}
