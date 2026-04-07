package uz.topdim.user.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uz.topdim.common.events.UserRegisteredEvent;
import uz.topdim.user.entity.User;
import uz.topdim.user.repository.ProcessedEventRepository;
import uz.topdim.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventListenerTest {

    @Mock private UserRepository userRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    private UserRegisteredEventListener listener;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        listener = new UserRegisteredEventListener(
                userRepository, processedEventRepository, new SimpleMeterRegistry());
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private String toJson(UserRegisteredEvent event) throws Exception {
        return objectMapper.writeValueAsString(event);
    }

    @Test
    @DisplayName("UserRegistered: создаёт профиль пользователя")
    void handleUserRegistered_createsProfile() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .eventId("evt-1")
                .eventType("UserRegistered")
                .eventVersion(1)
                .occurredAt(LocalDateTime.now())
                .correlationId("corr-1")
                .userId(42L)
                .email("user@topdim.uz")
                .firstName("Иван")
                .lastName("Иванов")
                .phone("+998901234567")
                .role("USER")
                .build();

        when(processedEventRepository.existsByEventId("evt-1")).thenReturn(false);
        when(userRepository.findByEmail("user@topdim.uz")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        listener.handleUserRegistered(toJson(event));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@topdim.uz");
        assertThat(saved.getFirstName()).isEqualTo("Иван");
        assertThat(saved.getLastName()).isEqualTo("Иванов");
        assertThat(saved.getPhone()).isEqualTo("+998901234567");
        assertThat(saved.getRole()).isEqualTo("USER");
        assertThat(saved.isEnabled()).isTrue();

        // Event marked as processed
        verify(processedEventRepository).save(argThat(pe ->
                pe.getEventId().equals("evt-1") && pe.getEventType().equals("UserRegistered")));
    }

    @Test
    @DisplayName("UserRegistered: duplicate event → no-op, не создаёт дубль")
    void handleUserRegistered_duplicateEvent_noop() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .eventId("evt-dup")
                .eventType("UserRegistered")
                .eventVersion(1)
                .occurredAt(LocalDateTime.now())
                .correlationId("corr-dup")
                .userId(42L)
                .email("dup@topdim.uz")
                .firstName("Дубликат")
                .role("USER")
                .build();

        when(processedEventRepository.existsByEventId("evt-dup")).thenReturn(true);

        listener.handleUserRegistered(toJson(event));

        verify(userRepository, never()).save(any());
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    @DisplayName("UserRegistered: профиль уже существует (по email) → no-op")
    void handleUserRegistered_profileAlreadyExists_noop() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .eventId("evt-exists")
                .eventType("UserRegistered")
                .eventVersion(1)
                .occurredAt(LocalDateTime.now())
                .correlationId("corr-exists")
                .userId(42L)
                .email("existing@topdim.uz")
                .firstName("Existing")
                .role("USER")
                .build();

        User existingUser = new User();
        existingUser.setId(99L);
        existingUser.setEmail("existing@topdim.uz");

        when(processedEventRepository.existsByEventId("evt-exists")).thenReturn(false);
        when(userRepository.findByEmail("existing@topdim.uz")).thenReturn(Optional.of(existingUser));

        listener.handleUserRegistered(toJson(event));

        verify(userRepository, never()).save(any());
        // Но processed_event записываем (чтобы не проверять повторно)
        verify(processedEventRepository).save(argThat(pe -> pe.getEventId().equals("evt-exists")));
    }

    @Test
    @DisplayName("UserRegistered: невалидный JSON → не ретраится, логирует ошибку")
    void handleUserRegistered_invalidJson_doesNotRetry() {
        listener.handleUserRegistered("{ invalid json }}}");

        verify(userRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    @DisplayName("UserRegistered: null обязательные поля → не ретраится")
    void handleUserRegistered_nullRequiredFields_doesNotRetry() throws Exception {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .eventId(null) // null eventId
                .eventType("UserRegistered")
                .userId(null) // null userId
                .email(null)  // null email
                .build();

        listener.handleUserRegistered(toJson(event));

        verify(userRepository, never()).save(any());
        verify(processedEventRepository, never()).save(any());
    }
}
