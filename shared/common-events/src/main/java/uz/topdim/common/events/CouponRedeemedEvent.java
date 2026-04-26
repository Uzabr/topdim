package uz.topdim.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Событие: купон погашён партнёром.
 * Публикуется order-service после успешного redeemCoupon/redeemByQrToken.
 * Слушается coupon-service для инкремента redeemedCount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponRedeemedEvent implements Serializable {
    private Long purchasedCouponId;
    private Long orderId;
    private Long couponOfferId;
    private Long couponOptionId;
    private Long merchantId;
    private LocalDateTime redeemedAt;
}
