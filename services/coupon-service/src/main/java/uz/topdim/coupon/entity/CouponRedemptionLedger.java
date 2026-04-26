package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Idempotency ledger для погашений купонов.
 * Уникальный ключ: purchasedCouponId —
 * гарантирует, что одно погашение не будет учтено дважды в redeemedCount.
 */
@Entity
@Table(name = "coupon_redemption_ledger",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_redemption_ledger_purchased_coupon",
                columnNames = {"purchased_coupon_id"}
        ))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRedemptionLedger {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "purchased_coupon_id", nullable = false)
    private Long purchasedCouponId;

    @Column(name = "coupon_offer_id", nullable = false)
    private Long couponOfferId;

    @Column(name = "coupon_option_id", nullable = false)
    private Long couponOptionId;

    @Column(name = "merchant_id")
    private Long merchantId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
