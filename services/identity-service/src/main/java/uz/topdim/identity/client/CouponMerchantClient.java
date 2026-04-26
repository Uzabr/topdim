package uz.topdim.identity.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uz.topdim.common.dto.ApiResponse;

@FeignClient(name = "coupon-service", path = "/api/v1")
public interface CouponMerchantClient {
    @PostMapping("/internal/merchants/onboarding")
    ApiResponse<MerchantOnboardingResponse> createMerchant(@RequestBody CreateMerchantOnboardingRequest request);
}
