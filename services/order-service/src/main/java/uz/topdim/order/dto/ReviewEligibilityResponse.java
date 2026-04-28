package uz.topdim.order.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Response DTO for review eligibility check.
 * Used by coupon-service to verify if a user can leave a review.
 */
@Data
@Builder
public class ReviewEligibilityResponse {
    private boolean eligible;
    private String reason;
    private Long purchasedCouponId;
    private LocalDateTime usedAt;
}
