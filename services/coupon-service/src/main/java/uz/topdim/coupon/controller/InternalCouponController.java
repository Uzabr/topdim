package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CouponPurchaseSnapshotResponse;
import uz.topdim.coupon.service.CouponOfferService;

@RestController
@RequestMapping("/api/v1/internal/coupons")
@RequiredArgsConstructor
public class InternalCouponController {
    private final CouponOfferService couponOfferService;

    @GetMapping("/{couponId}/options/{optionId}/purchase-snapshot")
    public ResponseEntity<ApiResponse<CouponPurchaseSnapshotResponse>> getPurchaseSnapshot(
            @PathVariable Long couponId,
            @PathVariable Long optionId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                couponOfferService.getPurchaseSnapshot(couponId, optionId)
        ));
    }
}
