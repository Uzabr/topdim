package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Изображение купона.
 * Хранит URL и порядок отображения.
 * Связана с CouponOffer.
 */
@Entity
@Table(name = "coupon_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_offer_id", nullable = false)
    private CouponOffer couponOffer;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "sort_order")
    private int sortOrder;
}
