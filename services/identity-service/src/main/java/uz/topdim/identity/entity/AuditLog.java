package uz.topdim.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Append-only лог действий администраторов и модераторов.
 * Записи не удаляются и не изменяются.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Email инициатора на момент действия (snapshot). */
    @Column(name = "user_email")
    private String userEmail;

    /** Отображаемое имя инициатора на момент действия (snapshot). */
    @Column(name = "user_name")
    private String userName;

    /** Роль инициатора на момент действия (snapshot). */
    @Column(name = "user_role")
    private String userRole;

    @Column(nullable = false)
    private String action;

    /** Раздел / сущность (users, staff, partner-applications, …). */
    @Column(name = "entity_name")
    private String entityName;

    @Column(name = "entity_id")
    private Long entityId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
