package uz.topdim.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.notification.client.IdentityNotificationTargetClient;
import uz.topdim.notification.client.InternalNotificationTargetResponse;
import uz.topdim.notification.entity.Notification;
import uz.topdim.notification.entity.NotificationDelivery;
import uz.topdim.notification.entity.NotificationDeliveryChannel;
import uz.topdim.notification.entity.NotificationDeliveryStatus;
import uz.topdim.notification.repository.NotificationDeliveryRepository;
import uz.topdim.notification.repository.NotificationRepository;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService {

    private static final int MAX_ATTEMPTS = 5;
    private static final int RETRY_BATCH_SIZE = 50;

    private final NotificationRepository notificationRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final IdentityNotificationTargetClient targetClient;
    private final TelegramNotificationSender telegramSender;
    private final EmailService emailService;

    @Transactional
    public void handle(NotificationEvent event) {
        if (event.getEventKey() == null || event.getEventKey().isBlank()) {
            createLegacyInApp(event);
            return;
        }
        if (notificationRepository.findByEventKeyAndUserId(
                event.getEventKey(), event.getUserId()).isPresent()) {
            return;
        }

        // Flush the idempotency key before any external side effect. A concurrent
        // duplicate then fails on the database constraint instead of sending twice.
        notificationRepository.saveAndFlush(Notification.builder()
                .eventKey(event.getEventKey())
                .userId(event.getUserId())
                .title(event.getTitle())
                .message(event.getMessage())
                .type(event.getType())
                .deepLink(event.getDeepLink())
                .read(false)
                .build());

        InternalNotificationTargetResponse target = resolveTarget(event.getUserId());
        if (target == null) {
            scheduleTargetResolutionRetry(event);
            return;
        }
        if (target.telegramLinked() && target.telegramChatId() != null) {
            deliverTelegram(event, target.telegramChatId());
        } else if (target.emailVerified() && hasText(target.email())) {
            deliverEmail(event, target.email());
        }
    }

    @Scheduled(fixedDelayString = "${notification.delivery.retry-delay-ms:60000}")
    @Transactional
    public int retryPendingDeliveries() {
        var deliveries = deliveryRepository.findRetryable(
                LocalDateTime.now(), PageRequest.of(0, RETRY_BATCH_SIZE));
        int processed = 0;
        for (NotificationDelivery delivery : deliveries) {
            retry(delivery);
            processed++;
        }
        return processed;
    }

    private void createLegacyInApp(NotificationEvent event) {
        notificationRepository.save(Notification.builder()
                .userId(event.getUserId())
                .title(event.getTitle())
                .message(event.getMessage())
                .type(event.getType())
                .deepLink(event.getDeepLink())
                .read(false)
                .build());
    }

    private InternalNotificationTargetResponse resolveTarget(Long userId) {
        try {
            ApiResponse<InternalNotificationTargetResponse> response = targetClient.getTarget(userId);
            if (response == null || !response.isSuccess()) {
                return null;
            }
            return response.getData();
        } catch (Exception exception) {
            log.warn("Notification target resolution failed for userId={}", userId);
            return null;
        }
    }

    private void deliverTelegram(NotificationEvent event, Long chatId) {
        NotificationDelivery delivery = getOrCreate(
                event.getEventKey(), event.getUserId(), NotificationDeliveryChannel.TELEGRAM);
        try {
            telegramSender.send(chatId, event.getMessage(), event.getDeepLink());
            markSent(delivery);
        } catch (Exception exception) {
            markRetryOrFailed(delivery, exception);
        }
        deliveryRepository.save(delivery);
    }

    private void scheduleTargetResolutionRetry(NotificationEvent event) {
        NotificationDelivery delivery = getOrCreate(
                event.getEventKey(), event.getUserId(), NotificationDeliveryChannel.TELEGRAM);
        delivery.setStatus(NotificationDeliveryStatus.RETRY);
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(LocalDateTime.now().plusMinutes(1));
        delivery.setLastError("TargetUnavailable");
        deliveryRepository.save(delivery);
    }

    private void deliverEmail(NotificationEvent event, String email) {
        NotificationDelivery delivery = getOrCreate(
                event.getEventKey(), event.getUserId(), NotificationDeliveryChannel.EMAIL);
        if (delivery.getStatus() == NotificationDeliveryStatus.SENT) {
            return;
        }
        try {
            emailService.sendProfileUpdateEmail(
                    email, event.getTitle(), event.getMessage(), event.getDeepLink());
            markSent(delivery);
        } catch (Exception exception) {
            markRetryOrFailed(delivery, exception);
        }
        deliveryRepository.save(delivery);
    }

    private void retry(NotificationDelivery delivery) {
        Notification notification = notificationRepository
                .findByEventKeyAndUserId(delivery.getEventKey(), delivery.getUserId())
                .orElse(null);
        if (notification == null) {
            markFailed(delivery, "NotificationNotFound");
            deliveryRepository.save(delivery);
            return;
        }
        NotificationEvent event = toEvent(notification);
        InternalNotificationTargetResponse target = resolveTarget(delivery.getUserId());
        if (target == null) {
            markRetryOrFailed(delivery, new IllegalStateException("TargetUnavailable"));
            deliveryRepository.save(delivery);
            return;
        }
        if (delivery.getChannel() == NotificationDeliveryChannel.TELEGRAM) {
            retryTelegram(delivery, event, target);
        } else {
            retryEmail(delivery, event, target);
        }
    }

    private void retryTelegram(
            NotificationDelivery delivery,
            NotificationEvent event,
            InternalNotificationTargetResponse target
    ) {
        if (!target.telegramLinked() || target.telegramChatId() == null) {
            markFailed(delivery, "TelegramUnavailable");
            deliveryRepository.save(delivery);
            deliverEmailFallback(event, target);
            return;
        }
        try {
            telegramSender.send(target.telegramChatId(), event.getMessage(), event.getDeepLink());
            markSent(delivery);
            deliveryRepository.save(delivery);
        } catch (Exception exception) {
            markRetryOrFailed(delivery, exception);
            deliveryRepository.save(delivery);
            if (delivery.getStatus() == NotificationDeliveryStatus.FAILED) {
                deliverEmailFallback(event, target);
            }
        }
    }

    private void retryEmail(
            NotificationDelivery delivery,
            NotificationEvent event,
            InternalNotificationTargetResponse target
    ) {
        if (!target.emailVerified() || !hasText(target.email())) {
            markFailed(delivery, "EmailUnavailable");
            deliveryRepository.save(delivery);
            return;
        }
        try {
            emailService.sendProfileUpdateEmail(
                    target.email(), event.getTitle(), event.getMessage(), event.getDeepLink());
            markSent(delivery);
        } catch (Exception exception) {
            markRetryOrFailed(delivery, exception);
        }
        deliveryRepository.save(delivery);
    }

    private void deliverEmailFallback(
            NotificationEvent event,
            InternalNotificationTargetResponse target
    ) {
        if (target.emailVerified() && hasText(target.email())) {
            deliverEmail(event, target.email());
        }
    }

    private NotificationDelivery getOrCreate(
            String eventKey,
            Long userId,
            NotificationDeliveryChannel channel
    ) {
        return deliveryRepository.findByEventKeyAndUserIdAndChannel(eventKey, userId, channel)
                .orElseGet(() -> deliveryRepository.save(NotificationDelivery.builder()
                        .eventKey(eventKey)
                        .userId(userId)
                        .channel(channel)
                        .status(NotificationDeliveryStatus.PENDING)
                        .attemptCount(0)
                        .build()));
    }

    private NotificationEvent toEvent(Notification notification) {
        return NotificationEvent.builder()
                .eventKey(notification.getEventKey())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .type(notification.getType())
                .deepLink(notification.getDeepLink())
                .timestamp(notification.getCreatedAt())
                .build();
    }

    private void markSent(NotificationDelivery delivery) {
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setStatus(NotificationDeliveryStatus.SENT);
        delivery.setSentAt(LocalDateTime.now());
        delivery.setNextAttemptAt(null);
        delivery.setLastError(null);
    }

    private void markRetryOrFailed(NotificationDelivery delivery, Exception exception) {
        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastError(safeError(exception));
        if (delivery.getAttemptCount() >= MAX_ATTEMPTS) {
            delivery.setStatus(NotificationDeliveryStatus.FAILED);
            delivery.setNextAttemptAt(null);
            log.error(
                    "Notification delivery exhausted retries: eventKey={}, userId={}, channel={}",
                    delivery.getEventKey(), delivery.getUserId(), delivery.getChannel());
        } else {
            delivery.setStatus(NotificationDeliveryStatus.RETRY);
            delivery.setNextAttemptAt(LocalDateTime.now()
                    .plusMinutes(backoffMinutes(delivery.getAttemptCount())));
        }
    }

    private void markFailed(NotificationDelivery delivery, String error) {
        delivery.setStatus(NotificationDeliveryStatus.FAILED);
        delivery.setNextAttemptAt(null);
        delivery.setLastError(error);
        log.error(
                "Notification delivery failed permanently: eventKey={}, userId={}, channel={}, reason={}",
                delivery.getEventKey(), delivery.getUserId(), delivery.getChannel(), error);
    }

    private long backoffMinutes(int attemptCount) {
        int exponent = Math.min(Math.max(attemptCount - 1, 0), 6);
        return Math.min(1L << exponent, 60L);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String safeError(Exception exception) {
        String name = exception.getClass().getSimpleName();
        return name.length() <= 500 ? name : name.substring(0, 500);
    }
}
