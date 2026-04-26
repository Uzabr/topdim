package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CouponPurchaseSnapshotResponse;
import uz.topdim.coupon.dto.RegisterCouponSaleRequest;
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

    @PostMapping("/{couponId}/options/{optionId}/sales")
    public ResponseEntity<ApiResponse<Void>> registerSale(
            @PathVariable Long couponId,
            @PathVariable Long optionId,
            @Valid @RequestBody RegisterCouponSaleRequest request
    ) {
        couponOfferService.registerSaleOnce(
                request.getOrderId(), couponId, optionId,
                request.getQuantity(), request.getAmount()
        );
        return ResponseEntity.ok(ApiResponse.success("Продажа зарегистрирована", null));
    }
}
