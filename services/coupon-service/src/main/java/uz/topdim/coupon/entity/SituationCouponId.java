package uz.topdim.coupon.entity;

import lombok.*;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key для {@link SituationCoupon}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SituationCouponId implements Serializable {

    private Long situation;
    private Long coupon;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SituationCouponId that = (SituationCouponId) o;
        return Objects.equals(situation, that.situation) && Objects.equals(coupon, that.coupon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(situation, coupon);
    }
}
