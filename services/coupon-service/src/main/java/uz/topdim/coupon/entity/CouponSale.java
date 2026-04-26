package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Idempotency ledger для регистрации продаж купонов.
 * Уникальный ключ: (orderId, couponOfferId, couponOptionId) —
 * гарантирует, что одна продажа из заказа не будет учтена дважды.
 */
@Entity
@Table(name = "coupon_sales",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_coupon_sales_order_offer_option",
                columnNames = {"order_id", "coupon_offer_id", "coupon_option_id"}
        ))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponSale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "coupon_offer_id", nullable = false)
    private Long couponOfferId;

    @Column(name = "coupon_option_id", nullable = false)
    private Long couponOptionId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
