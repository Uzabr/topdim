package uz.topdim.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.auth.entity.OutboxEvent;
import uz.topdim.auth.entity.User;
import uz.topdim.auth.repository.OutboxEventRepository;
import uz.topdim.common.events.UserRegisteredEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Сервис для создания outbox записей.
 * Вызывается внутри транзакции AuthService.register()/guestAuth().
 *
 * <p>Гарантия: outbox запись создаётся в той же транзакции, что и User.
 * Если транзакция откатывается — outbox запись тоже откатывается.
 */
@Slf4j
@Service
public class OutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Counter outboxCreatedCounter;

    public OutboxService(OutboxEventRepository outboxEventRepository, MeterRegistry meterRegistry) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.outboxCreatedCounter = Counter.builder("topdim.outbox.events.created")
                .description("Number of outbox events created")
                .tag("event_type", "UserRegistered")
                .register(meterRegistry);
    }

    /**
     * Создаёт outbox запись для события UserRegistered.
     * MUST be called within the same @Transactional as userRepository.save().
     *
     * @param user          сохранённый пользователь (с ID)
     * @param correlationId correlation ID для трейсинга
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void createUserRegisteredEvent(User user, String correlationId) {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("UserRegistered")
                .eventVersion(1)
                .occurredAt(LocalDateTime.now())
                .correlationId(correlationId)
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .build();

        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.error("OUTBOX: Failed to serialize UserRegisteredEvent for userId={}: {}",
                    user.getId(), e.getMessage());
            throw new RuntimeException("Failed to serialize outbox event", e);
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventId(event.getEventId())
                .eventType("UserRegistered")
                .eventVersion(1)
                .aggregateType("User")
                .aggregateId(user.getId())
                .payload(payload)
                .status("PENDING")
                .attemptCount(0)
                .correlationId(correlationId)
                .build();

        outboxEventRepository.save(outboxEvent);
        outboxCreatedCounter.increment();

        log.info("OUTBOX: Event created [eventId={}, type=UserRegistered, userId={}, correlationId={}]",
                event.getEventId(), user.getId(), correlationId);
    }
}
