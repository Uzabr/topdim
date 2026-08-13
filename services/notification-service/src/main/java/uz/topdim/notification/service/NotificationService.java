package uz.topdim.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.notification.dto.NotificationResponse;
import uz.topdim.notification.entity.Notification;
import uz.topdim.notification.repository.NotificationRepository;

@Service
@RequiredArgsConstructor
public class NotificationService {
    
    private final NotificationRepository notificationRepository;

    @Transactional
    public void createNotification(Long userId, String title, String message, String type) {
        Notification notification = Notification.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .read(false)
                .build();
        notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getUserNotifications(Long userId, Boolean unreadOnly, Pageable pageable) {
        Page<Notification> notifications;
        if (unreadOnly != null && unreadOnly) {
            notifications = notificationRepository.findAllByUserIdAndReadOrderByCreatedAtDesc(userId, false, pageable);
        } else {
            notifications = notificationRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return notifications.map(this::mapToResponse);
    }

    @Transactional
    public void markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Уведомление не найдено"));
        if (!notification.getUserId().equals(userId)) {
            throw new RuntimeException("Нет доступа к этому уведомлению");
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .deepLink(n.getDeepLink())
                .read(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
