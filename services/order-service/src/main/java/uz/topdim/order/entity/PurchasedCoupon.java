package uz.topdim.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "purchased_coupons")
@Getter @Setter @Builder @NoArgsConstructor /**
 * Купленный купон.
 * Генерируется после оплаты с уникальным кодом и QR токеном.
 * Статусы: ACTIVE → USED / EXPIRED / REFUNDED.
 */
@AllArgsConstructor
public class PurchasedCoupon {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "coupon_offer_id", nullable = false)
    private Long couponOfferId;

    @Column(name = "coupon_option_id", nullable = false)
    private Long couponOptionId;

    @Column(name = "coupon_title")
    private String couponTitle;

    @Column(name = "option_title")
    private String optionTitle;

    @Column(name = "coupon_code", nullable = false, unique = true)
    private String couponCode;

    @Column(name = "qr_token", unique = true)
    private String qrToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchasedCouponStatus status;

    @CreationTimestamp
    @Column(name = "purchased_at", updatable = false)
    private LocalDateTime purchasedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "is_gift")
    private boolean gift;

    @Column(name = "gift_recipient_name")
    private String giftRecipientName;

    @Column(name = "gift_recipient_phone")
    private String giftRecipientPhone;
}
