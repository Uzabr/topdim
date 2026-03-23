package uz.topdim.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import uz.topdim.common.dto.ApiResponse;

import java.util.Map;

@FeignClient(name = "coupon-service", path = "/api/v1")
public interface CouponClient {

    @GetMapping("/coupons/{id}")
    ApiResponse<Map<String, Object>> getCouponById(@PathVariable("id") Long id);

    @GetMapping("/coupons/{couponId}/options/{optionId}")
    ApiResponse<Map<String, Object>> getCouponOption(
            @PathVariable("couponId") Long couponId,
            @PathVariable("optionId") Long optionId
    );
}
