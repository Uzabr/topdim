package uz.topdim.order.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
@Getter @Setter @Builder @NoArgsConstructor /**
 * Элемент заказа.
 * Связан с конкретным купоном и опцией.
 * Хранит цену на момент покупки.
 */
@AllArgsConstructor
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "unit_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "is_gift")
    private boolean gift;

    @Column(name = "gift_recipient_name")
    private String giftRecipientName;

    @Column(name = "gift_recipient_phone")
    private String giftRecipientPhone;

    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "merchant_address", columnDefinition = "TEXT")
    private String merchantAddress;

    @Column(name = "merchant_phone")
    private String merchantPhone;

    @Column(name = "merchant_working_hours")
    private String merchantWorkingHours;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
