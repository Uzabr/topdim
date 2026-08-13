package uz.topdim.coupon.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.coupon.entity.MerchantProfileChangeHistory;
import uz.topdim.coupon.entity.MerchantProfileChangeRequest;
import uz.topdim.coupon.entity.MerchantProfileChangeStatus;
import uz.topdim.coupon.entity.NotificationOutbox;
import uz.topdim.coupon.entity.NotificationOutboxStatus;
import uz.topdim.coupon.repository.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MerchantProfileOutboxService {

    private static final String PARTNER_REQUEST_LINK = "/company/requests/";

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void enqueue(
            MerchantProfileChangeRequest request,
            MerchantProfileChangeHistory history
    ) {
        if (request.getId() == null || history.getId() == null) {
            throw new IllegalArgumentException("Заявка и запись истории должны быть сохранены");
        }

        Set<Long> recipients = new LinkedHashSet<>();
        recipients.add(request.getAuthorUserId());
        if (request.getMerchant().getUserId() != null) {
            recipients.add(request.getMerchant().getUserId());
        }
        recipients.remove(null);

        for (Long recipientUserId : recipients) {
            enqueueRecipient(request, history, recipientUserId);
        }
    }

    private void enqueueRecipient(
            MerchantProfileChangeRequest request,
            MerchantProfileChangeHistory history,
            Long recipientUserId
    ) {
        String eventKey = "merchant-profile:%d:%d:%d".formatted(
                request.getId(), history.getId(), recipientUserId);
        if (outboxRepository.existsByEventKey(eventKey)) {
            return;
        }

        LocalDateTime occurredAt = history.getCreatedAt() == null
                ? LocalDateTime.now()
                : history.getCreatedAt();
        NotificationEvent event = NotificationEvent.builder()
                .eventKey(eventKey)
                .userId(recipientUserId)
                .title(title(history.getNewStatus()))
                .message(message(request, history))
                .type(type(history.getNewStatus()))
                .deepLink(PARTNER_REQUEST_LINK + request.getId())
                .timestamp(occurredAt)
                .build();

        outboxRepository.save(NotificationOutbox.builder()
                .eventKey(eventKey)
                .recipientUserId(recipientUserId)
                .payload(serialize(event))
                .status(NotificationOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(occurredAt)
                .build());
    }

    private String serialize(NotificationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Не удалось сформировать событие уведомления", exception);
        }
    }

    private String title(MerchantProfileChangeStatus status) {
        return switch (status) {
            case PENDING_REVIEW -> "Заявка отправлена на модерацию";
            case IN_REVIEW -> "Заявка принята в работу";
            case REVISION_REQUESTED -> "Заявка возвращена на доработку";
            case APPROVED -> "Изменения компании одобрены";
            case REJECTED -> "Заявка отклонена";
            case WITHDRAWN -> "Заявка отозвана";
            case OUTDATED -> "Заявка устарела";
            case DRAFT -> "Черновик заявки создан";
        };
    }

    private String message(
            MerchantProfileChangeRequest request,
            MerchantProfileChangeHistory history
    ) {
        String message = "Компания «%s», заявка #%d: %s".formatted(
                request.getMerchant().getName(),
                request.getId(),
                history.getNewStatus().name());
        if (history.getComment() != null && !history.getComment().isBlank()) {
            return message + ". Комментарий: " + history.getComment();
        }
        return message;
    }

    private String type(MerchantProfileChangeStatus status) {
        return switch (status) {
            case APPROVED -> "SUCCESS";
            case REJECTED, OUTDATED -> "ALERT";
            case REVISION_REQUESTED -> "WARNING";
            default -> "INFO";
        };
    }
}
