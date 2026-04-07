package uz.topdim.user.listener;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.events.UserRegisteredEvent;
import uz.topdim.user.entity.ProcessedEvent;
import uz.topdim.user.entity.User;
import uz.topdim.user.repository.ProcessedEventRepository;
import uz.topdim.user.repository.UserRepository;

import java.time.LocalDateTime;

/**
 * Слушатель событий UserRegistered (RabbitMQ).
 * Создаёт профиль пользователя в user-service при получении события из auth-service.
 *
 * <p>Идемпотентность:
 * <ul>
 *   <li>Проверка processed_events по eventId</li>
 *   <li>Проверка users по email</li>
 *   <li>Duplicate → success no-op</li>
 * </ul>
 *
 * <p>Ошибки:
 * <ul>
 *   <li>Temporary (DB timeout) → Spring Retry (3 попытки) → DLQ</li>
 *   <li>Invalid message → log + no-op (не ретраится)</li>
 * </ul>
 */
@Slf4j
@Component
public class UserRegisteredEventListener {

    private final UserRepository userRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    private final Counter eventsReceivedCounter;
    private final Counter profilesCreatedCounter;
    private final Counter duplicateCounter;
    private final Counter failedCounter;

    public UserRegisteredEventListener(UserRepository userRepository,
                                       ProcessedEventRepository processedEventRepository,
                                       MeterRegistry meterRegistry) {
        this.userRepository = userRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());

        this.eventsReceivedCounter = Counter.builder("topdim.user.events.received")
                .description("Number of UserRegistered events received")
                .register(meterRegistry);
        this.profilesCreatedCounter = Counter.builder("topdim.user.profiles.created")
                .description("Number of user profiles created from events")
                .register(meterRegistry);
        this.duplicateCounter = Counter.builder("topdim.user.events.duplicate")
                .description("Number of duplicate events handled as no-op")
                .register(meterRegistry);
        this.failedCounter = Counter.builder("topdim.user.events.failed")
                .description("Number of failed event processings")
                .register(meterRegistry);
    }

    /**
     * Обработчик события UserRegistered.
     * Payload приходит как JSON-строка, десериализуется в UserRegisteredEvent.
     *
     * @param payload JSON строка с UserRegisteredEvent
     */
    @RabbitListener(queues = "user.registered.queue")
    @Transactional
    public void handleUserRegistered(String payload) {
        eventsReceivedCounter.increment();

        UserRegisteredEvent event;
        try {
            event = objectMapper.readValue(payload, UserRegisteredEvent.class);
        } catch (JsonProcessingException e) {
            // Невалидный JSON → не ретраим, логируем и выходим
            log.error("EVENT_LISTENER: Failed to deserialize UserRegisteredEvent: {}", e.getMessage());
            failedCounter.increment();
            return; // acknowledge — не возвращаем в очередь
        }

        String eventId = event.getEventId();
        String correlationId = event.getCorrelationId();

        log.info("EVENT_LISTENER: Received UserRegistered [eventId={}, userId={}, email={}, correlationId={}]",
                eventId, event.getUserId(), event.getEmail(), correlationId);

        // Валидация обязательных полей
        if (eventId == null || event.getUserId() == null || event.getEmail() == null) {
            log.error("EVENT_LISTENER: Invalid event — missing required fields [eventId={}, correlationId={}]",
                    eventId, correlationId);
            failedCounter.increment();
            return; // acknowledge — невалидное сообщение не ретраим
        }

        // Idempotency check 1: уже обработали это событие?
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("EVENT_LISTENER: Duplicate event, no-op [eventId={}, correlationId={}]",
                    eventId, correlationId);
            duplicateCounter.increment();
            return;
        }

        // Idempotency check 2: профиль уже существует?
        if (userRepository.findByEmail(event.getEmail()).isPresent()) {
            log.info("EVENT_LISTENER: Profile already exists for email={}, saving as processed [eventId={}, correlationId={}]",
                    event.getEmail(), eventId, correlationId);
            saveProcessedEvent(eventId, "UserRegistered");
            duplicateCounter.increment();
            return;
        }

        // Создаём профиль пользователя
        User user = new User();
        user.setEmail(event.getEmail());
        user.setFirstName(event.getFirstName());
        user.setLastName(event.getLastName());
        user.setPhone(event.getPhone());
        user.setRole(event.getRole());
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setPhoneVerified(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userRepository.save(user);

        // Записываем как обработанный
        saveProcessedEvent(eventId, "UserRegistered");

        profilesCreatedCounter.increment();

        log.info("EVENT_LISTENER: Profile created [userId={}, email={}, eventId={}, correlationId={}]",
                user.getId(), event.getEmail(), eventId, correlationId);
    }

    private void saveProcessedEvent(String eventId, String eventType) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .build();
        processedEventRepository.save(processedEvent);
    }
}
