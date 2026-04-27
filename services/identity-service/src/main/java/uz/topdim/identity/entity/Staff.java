package uz.topdim.identity.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Сущность сотрудника партнёра.
 * Партнёр (userId) добавляет сотрудников, которые могут погашать его купоны.
 * loginUserId — ID пользователя в таблице users для независимого входа кассира.
 * merchantId — ID мерчанта из coupon-service.
 * merchantLocationId — ID филиала, к которому привязан кассир.
 */
@Entity
@Table(name = "staff")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ID партнёра-владельца
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // ID пользователя для самостоятельного логина кассира (ссылка на users.id)
    @Column(name = "login_user_id")
    private Long loginUserId;

    // ID мерчанта из coupon-service
    @Column(name = "merchant_id")
    private Long merchantId;

    // ID филиала (MerchantLocation) — обязателен для CASHIER
    @Column(name = "merchant_location_id")
    private Long merchantLocationId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String phone;

    @Column(nullable = false)
    @Builder.Default
    private String role = "CASHIER";

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
