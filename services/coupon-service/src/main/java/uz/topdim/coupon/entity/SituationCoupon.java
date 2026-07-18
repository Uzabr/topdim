package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Join-entity: привязка купона к ситуации.
 * Composite PK: (situation_id, coupon_id).
 * sortOrder задаётся порядком couponIds в админском запросе.
 */
@Entity
@Table(name = "situation_coupons")
@IdClass(SituationCouponId.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SituationCoupon {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "situation_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Situation situation;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "coupon_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CouponOffer coupon;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
