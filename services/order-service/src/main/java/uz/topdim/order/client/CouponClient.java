package uz.topdim.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import uz.topdim.common.dto.ApiResponse;

import java.math.BigDecimal;

/**
 * OpenFeign клиент для coupon-service.
 * Получение snapshot купона для валидации покупки.
 * Регистрация продаж (sale registration).
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

    @PostMapping("/internal/coupons/{couponId}/options/{optionId}/sales")
    ApiResponse<Void> registerSale(
            @PathVariable("couponId") Long couponId,
            @PathVariable("optionId") Long optionId,
            @RequestBody RegisterSaleRequest request
    );
}
