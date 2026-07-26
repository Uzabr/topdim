package uz.topdim.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность пользователя (объединённая из auth-service + user-service).
 * Хранит email, телефон, хэш пароля, роль и профильные данные.
 * Связана с RefreshToken для управления сессиями.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(unique = true)
    private String phone;

    @Column(nullable = false)
    private String password;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name")
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * Версия безопасности. Инкрементируется при:
     * change-password, block/unblock, change-role.
     * Gateway сравнивает securityVersion из JWT с текущим значением в Redis.
     * Если не совпадает → токен невалиден.
     */
    @Column(name = "security_version", nullable = false)
    @Builder.Default
    private long securityVersion = 0;

    @Column(name = "email_verified")
    private boolean emailVerified;

    @Column(name = "phone_verified")
    private boolean phoneVerified;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "avatar_url")
    private String avatarUrl;

    // ==================== Telegram / уровень доверия ====================

    /** Telegram user id — ключ привязки/логина через Telegram. */
    @Column(name = "telegram_chat_id", unique = true)
    private Long telegramChatId;

    @Column(name = "telegram_username")
    private String telegramUsername;

    @Column(name = "telegram_linked_at")
    private LocalDateTime telegramLinkedAt;

    // ==================== Google OAuth / Оплата ====================

    @Column(name = "google_sub", unique = true)
    private String googleSub;

    /** Время первой успешной оплаты. Не null → «была оплата» (навсегда), вклад в L1. */
    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    // ==================== Уровень доверия ====================

    /** Уровень доверия (ортогонален роли). По умолчанию L0. */
    @Enumerated(EnumType.STRING)
    @Column(name = "trust_level", nullable = false)
    @Builder.Default
    private TrustLevel trustLevel = TrustLevel.L0;

    @Column
    private boolean deleted;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
