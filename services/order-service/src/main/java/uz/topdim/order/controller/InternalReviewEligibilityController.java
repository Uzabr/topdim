package uz.topdim.order.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.dto.ReviewEligibilityResponse;
import uz.topdim.order.service.OrderService;

/**
 * Internal endpoint for inter-service review eligibility checks.
 * Called by coupon-service via Feign before accepting a review.
 * Not exposed through API Gateway to public users.
 */
@RestController
@RequestMapping("/api/v1/internal/reviews")
@RequiredArgsConstructor
public class InternalReviewEligibilityController {

    private final OrderService orderService;

    @GetMapping("/eligibility")
    public ResponseEntity<ApiResponse<ReviewEligibilityResponse>> getEligibility(
            @RequestParam Long userId,
            @RequestParam Long couponOfferId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                orderService.getReviewEligibility(userId, couponOfferId)
        ));
    }
}
