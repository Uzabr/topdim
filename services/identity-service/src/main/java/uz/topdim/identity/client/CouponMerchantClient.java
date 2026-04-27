package uz.topdim.identity.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uz.topdim.common.dto.ApiResponse;

@FeignClient(name = "coupon-service", path = "/api/v1")
public interface CouponMerchantClient {
    @PostMapping("/internal/merchants/onboarding")
    ApiResponse<MerchantOnboardingResponse> createMerchant(@RequestBody CreateMerchantOnboardingRequest request);

    /** Получить контекст мерчанта по userId владельца */
    @GetMapping("/internal/merchants/by-user/{userId}")
    ApiResponse<MerchantOnboardingResponse> getMerchantByUserId(@PathVariable("userId") Long userId);

    /** Получить активные локации мерчанта по userId владельца */
    @GetMapping("/internal/merchants/by-user/{userId}/locations")
    ApiResponse<java.util.List<MerchantLocationResponse>> getMerchantLocationsByUserId(@PathVariable("userId") Long userId);
}
