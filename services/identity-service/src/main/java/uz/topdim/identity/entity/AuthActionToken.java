package uz.topdim.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Одноразовый токен для auth-действий (password reset, email/phone confirm).
 *
 * <p>Жизненный цикл:
 * <ol>
 *   <li>Создаётся при request (password-reset/request, confirm/request)</li>
 *   <li>Хранит SHA-256 hash токена (не plain text)</li>
 *   <li>При confirm — проверяется hash, помечается used_at</li>
 *   <li>Старые неиспользованные токены revoke'ятся при создании нового</li>
 * </ol>
 */
@Entity
@Table(name = "auth_action_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthActionToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AuthActionType type;

    /** SHA-256 hash токена. Plain text не хранится. */
    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    /** Email или phone, куда отправлен код/ссылка. */
    @Column
    private String target;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(nullable = false)
    @Builder.Default
    private boolean revoked = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_sent_at")
    private LocalDateTime lastSentAt;

    /** Токен валиден, если не использован, не отозван и не истёк. */
    public boolean isValid() {
        return usedAt == null
                && !revoked
                && expiresAt.isAfter(LocalDateTime.now());
    }
}
