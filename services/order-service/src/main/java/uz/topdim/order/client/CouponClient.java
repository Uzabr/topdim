package uz.topdim.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uz.topdim.common.dto.ApiResponse;

/**
 * OpenFeign клиент для coupon-service.
 * Получение snapshot купона для валидации покупки.
 */
@FeignClient(name = "coupon-service", path = "/api/v1")
public interface CouponClient {

    @GetMapping("/internal/coupons/{couponId}/options/{optionId}/purchase-snapshot")
    ApiResponse<CouponPurchaseSnapshot> getPurchaseSnapshot(
            @PathVariable("couponId") Long couponId,
            @PathVariable("optionId") Long optionId
    );

    @GetMapping("/internal/merchants/by-user/{userId}")
    ApiResponse<MerchantContext> getMerchantContextByUserId(@PathVariable("userId") Long userId);
}
