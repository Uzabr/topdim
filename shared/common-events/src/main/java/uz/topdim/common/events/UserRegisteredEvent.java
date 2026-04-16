package uz.topdim.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Событие регистрации пользователя (RabbitMQ).
 * Публикуется auth-service → слушает user-service.
 *
 * <p>Содержит только безопасные данные:
 * НЕ включает password, password hash, token и другие секреты.
 *
 * <p>Поток: AuthService.register()/guestAuth() → Outbox table → OutboxPublisher → RabbitMQ → UserRegisteredEventListener
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisteredEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Уникальный идентификатор события (UUID). */
    private String eventId;

    /** Тип события: "UserRegistered". */
    private String eventType;

    /** Версия схемы события. */
    private int eventVersion;

    /** Время создания события. */
    private LocalDateTime occurredAt;

    /** Correlation ID для сквозного трейсинга (UUID). */
    private String correlationId;

    /** ID пользователя из auth-service (BIGSERIAL). */
    private Long userId;

    /** Email пользователя. */
    private String email;

    /** Имя пользователя. */
    private String firstName;

    /** Фамилия пользователя (может быть null). */
    private String lastName;

    /** Телефон пользователя (может быть null). */
    private String phone;

    /** Роль: "USER" или "GUEST". */
    private String role;
}
