package uz.topdim.coupon.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import uz.topdim.common.events.NotificationEvent;
import uz.topdim.coupon.entity.NotificationOutbox;
import uz.topdim.coupon.entity.NotificationOutboxStatus;
import uz.topdim.coupon.repository.NotificationOutboxRepository;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxPublisherTest {

    @Mock private NotificationOutboxRepository repository;
    @Mock private RabbitTemplate rabbitTemplate;

    private ObjectMapper objectMapper;
    private NotificationOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        publisher = new NotificationOutboxPublisher(repository, rabbitTemplate, objectMapper);
    }

    @Test
    void successfulPublishMarksClaimedRowPublished() throws Exception {
        NotificationOutbox row = pendingRow("evt-1");
        when(repository.findPublishable(any(), any())).thenReturn(List.of(row));

        int published = publisher.publishPending();

        assertThat(published).isEqualTo(1);
        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.PUBLISHED);
        assertThat(row.getPublishedAt()).isNotNull();
        assertThat(row.getAttemptCount()).isZero();
        verify(rabbitTemplate).convertAndSend(
                "notification.exchange", "notification.sent",
                objectMapper.readValue(row.getPayload(), NotificationEvent.class));
    }

    @Test
    void failedPublishSchedulesBoundedRetryWithoutThrowing() throws Exception {
        NotificationOutbox row = pendingRow("evt-2");
        row.setAttemptCount(20);
        LocalDateTime before = LocalDateTime.now();
        when(repository.findPublishable(any(), any())).thenReturn(List.of(row));
        doThrow(new AmqpException("broker unavailable"))
                .when(rabbitTemplate)
                .convertAndSend(
                        "notification.exchange", "notification.sent",
                        objectMapper.readValue(row.getPayload(), NotificationEvent.class));

        int published = publisher.publishPending();

        assertThat(published).isZero();
        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(row.getAttemptCount()).isEqualTo(21);
        assertThat(row.getPublishedAt()).isNull();
        assertThat(row.getNextAttemptAt())
                .isAfterOrEqualTo(before.plusMinutes(59))
                .isBeforeOrEqualTo(LocalDateTime.now().plusMinutes(61));
        verify(repository).save(row);
    }

    private NotificationOutbox pendingRow(String eventKey) throws Exception {
        NotificationEvent event = NotificationEvent.builder()
                .eventKey(eventKey)
                .userId(41L)
                .title("Статус заявки")
                .message("Заявка обновлена")
                .type("INFO")
                .deepLink("/company/requests/7")
                .timestamp(LocalDateTime.of(2026, 8, 13, 10, 0))
                .build();
        return NotificationOutbox.builder()
                .eventKey(eventKey)
                .recipientUserId(41L)
                .payload(objectMapper.writeValueAsString(event))
                .status(NotificationOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(LocalDateTime.of(2026, 8, 13, 10, 0))
                .build();
    }
}
