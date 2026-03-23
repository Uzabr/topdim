package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "coupon_options")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_offer_id", nullable = false)
    private CouponOffer couponOffer;

    @Column(nullable = false)
    private String title;

    @Column(name = "regular_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal regularPrice;

    @Column(name = "coupon_price", precision = 12, scale = 2, nullable = false)
    private BigDecimal couponPrice;

    @Column(name = "quantity_limit")
    private Integer quantityLimit;

    @Column(name = "quantity_sold")
    private int quantitySold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponOptionStatus status;
}
