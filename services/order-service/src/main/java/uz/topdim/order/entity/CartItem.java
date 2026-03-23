package uz.topdim.order.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "cart_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

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
}
