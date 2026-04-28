package uz.topdim.coupon.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import uz.topdim.common.dto.ApiResponse;

/**
 * Feign client for order-service review eligibility endpoint.
 * Checks if user has a USED purchased coupon for a given couponOfferId.
 */
@FeignClient(name = "order-service", path = "/api/v1")
public interface OrderReviewEligibilityClient {
    @GetMapping("/internal/reviews/eligibility")
    ApiResponse<ReviewEligibilityResponse> getReviewEligibility(
            @RequestParam("userId") Long userId,
            @RequestParam("couponOfferId") Long couponOfferId
    );
}
