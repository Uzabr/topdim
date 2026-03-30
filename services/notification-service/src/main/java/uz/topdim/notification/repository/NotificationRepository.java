package uz.topdim.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findAllByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Notification> findAllByUserIdAndReadOrderByCreatedAtDesc(Long userId, boolean read, Pageable pageable);
}
