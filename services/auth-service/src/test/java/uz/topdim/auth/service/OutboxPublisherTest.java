package uz.topdim.auth.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.amqp.core.MessagePostProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import uz.topdim.auth.entity.OutboxEvent;
import uz.topdim.auth.repository.OutboxEventRepository;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private RabbitTemplate rabbitTemplate;
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, rabbitTemplate, new SimpleMeterRegistry());
    }

    @Test
    @DisplayName("publishPendingEvents: нет PENDING событий → ничего не делает")
    void publishPendingEvents_noPending_doesNothing() {
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(Collections.emptyList());

        outboxPublisher.publishPendingEvents();

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
    }

    @Test
    @DisplayName("publishPendingEvents: успешная публикация → status = PUBLISHED")
    void publishPendingEvents_success_marksAsPublished() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L).eventId("evt-1").eventType("UserRegistered")
                .aggregateType("User").aggregateId(42L)
                .payload("{\"userId\":42}").status("PENDING")
                .attemptCount(0).correlationId("corr-1")
                .createdAt(LocalDateTime.now())
                .build();

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(List.of(event));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher.publishPendingEvents();

        verify(rabbitTemplate).convertAndSend(eq("user.exchange"), eq("user.registered"), eq("{\"userId\":42}"), any(MessagePostProcessor.class));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo("PUBLISHED");
        assertThat(captor.getValue().getPublishedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishPendingEvents: ошибка broker → attempt_count++ и last_error")
    void publishPendingEvents_brokerError_incrementsAttempt() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L).eventId("evt-2").eventType("UserRegistered")
                .aggregateType("User").aggregateId(42L)
                .payload("{\"userId\":42}").status("PENDING")
                .attemptCount(0).correlationId("corr-2")
                .createdAt(LocalDateTime.now())
                .build();

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("Connection refused"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo("PENDING"); // всё ещё PENDING
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(1);
        assertThat(captor.getValue().getLastError()).contains("Connection refused");
    }

    @Test
    @DisplayName("publishPendingEvents: 10+ попыток → status = FAILED")
    void publishPendingEvents_maxAttempts_marksAsFailed() {
        OutboxEvent event = OutboxEvent.builder()
                .id(1L).eventId("evt-3").eventType("UserRegistered")
                .aggregateType("User").aggregateId(42L)
                .payload("{\"userId\":42}").status("PENDING")
                .attemptCount(9).correlationId("corr-3") // 9 → будет 10
                .createdAt(LocalDateTime.now())
                .build();

        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc(eq("PENDING"), any(PageRequest.class)))
                .thenReturn(List.of(event));
        doThrow(new RuntimeException("Broker down"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class), any(MessagePostProcessor.class));
        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(inv -> inv.getArgument(0));

        outboxPublisher.publishPendingEvents();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getAttemptCount()).isEqualTo(10);
    }
}
