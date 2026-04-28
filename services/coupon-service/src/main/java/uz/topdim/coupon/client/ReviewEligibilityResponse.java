package uz.topdim.coupon.client;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO matching order-service ReviewEligibilityResponse.
 */
@Data
public class ReviewEligibilityResponse {
    private boolean eligible;
    private String reason;
    private Long purchasedCouponId;
    private LocalDateTime usedAt;
}
