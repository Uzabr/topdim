package uz.topdim.auth.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.auth.entity.OutboxEvent;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;
import uz.topdim.auth.repository.OutboxEventRepository;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {

    @Mock private OutboxEventRepository outboxEventRepository;
    private MeterRegistry meterRegistry;
    private OutboxService outboxService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        outboxService = new OutboxService(outboxEventRepository, meterRegistry);
    }

    @Test
    @DisplayName("createUserRegisteredEvent: создаёт outbox запись с правильным payload")
    void createUserRegisteredEvent_savesCorrectOutboxEvent() {
        User user = User.builder()
                .id(42L)
                .email("test@topdim.uz")
                .firstName("Иван")
                .lastName("Иванов")
                .phone("+998901234567")
                .role(Role.USER)
                .build();

        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        outboxService.createUserRegisteredEvent(user, "corr-123");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventId()).isNotBlank();
        assertThat(saved.getEventType()).isEqualTo("UserRegistered");
        assertThat(saved.getEventVersion()).isEqualTo(1);
        assertThat(saved.getAggregateType()).isEqualTo("User");
        assertThat(saved.getAggregateId()).isEqualTo(42L);
        assertThat(saved.getStatus()).isEqualTo("PENDING");
        assertThat(saved.getAttemptCount()).isEqualTo(0);
        assertThat(saved.getCorrelationId()).isEqualTo("corr-123");

        // Payload содержит JSON с данными пользователя (без пароля)
        assertThat(saved.getPayload()).contains("\"userId\":42");
        assertThat(saved.getPayload()).contains("\"email\":\"test@topdim.uz\"");
        assertThat(saved.getPayload()).contains("\"firstName\":\"Иван\"");
        assertThat(saved.getPayload()).contains("\"role\":\"USER\"");
        assertThat(saved.getPayload()).doesNotContain("password");
    }

    @Test
    @DisplayName("createUserRegisteredEvent: метрика outbox.events.created инкрементируется")
    void createUserRegisteredEvent_incrementsMetric() {
        User user = User.builder()
                .id(1L).email("a@b.com").firstName("A").role(Role.USER).build();
        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.createUserRegisteredEvent(user, "corr-456");

        Counter counter = meterRegistry.find("topdim.outbox.events.created").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("createUserRegisteredEvent: GUEST пользователь сохраняется корректно")
    void createUserRegisteredEvent_guestUser_savesCorrectRole() {
        User user = User.builder()
                .id(100L)
                .email("guest_+998901234567@topdim.uz")
                .firstName("Гость")
                .phone("+998901234567")
                .role(Role.GUEST)
                .build();

        when(outboxEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        outboxService.createUserRegisteredEvent(user, "corr-guest");

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());

        assertThat(captor.getValue().getPayload()).contains("\"role\":\"GUEST\"");
    }
}
