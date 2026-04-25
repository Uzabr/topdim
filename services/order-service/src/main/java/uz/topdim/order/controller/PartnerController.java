package uz.topdim.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.dto.CreateRedemptionRequest;
import uz.topdim.order.dto.PartnerStatsResponse;
import uz.topdim.order.dto.RedeemCouponResponse;
import uz.topdim.order.dto.RedemptionResponse;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.service.OrderService;
import uz.topdim.order.service.PartnerMerchantResolver;
import uz.topdim.order.service.PartnerService;

/**
 * Контроллер партнёра — погашение купонов, статистика и история.
 * merchantId резолвится из доверенного X-User-Id через coupon-service.
 */
@RestController
@RequestMapping("/api/v1/partner")
@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;
    private final OrderService orderService;
    private final PartnerMerchantResolver partnerMerchantResolver;

    /** Погашение купона по PIN-коду. */
    @PostMapping("/redemptions")
    public ResponseEntity<ApiResponse<RedeemCouponResponse>> createRedemption(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateRedemptionRequest request
    ) {
        Long merchantId = partnerMerchantResolver.resolveMerchantId(userId);
        String couponCode = request.getCouponCode().trim().toUpperCase();
        PurchasedCoupon coupon = orderService.redeemCoupon(couponCode, merchantId, request.getStaffName());
        return ResponseEntity.ok(ApiResponse.success("Купон использован", orderService.mapToRedeemResponse(coupon)));
    }

    /** Статистика продаж партнёра. */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PartnerStatsResponse>> getStats(
            @RequestHeader("X-User-Id") Long userId
    ) {
        Long merchantId = partnerMerchantResolver.resolveMerchantId(userId);
        return ResponseEntity.ok(ApiResponse.success(partnerService.getStats(merchantId)));
    }

    /** История погашений. */
    @GetMapping("/redemptions")
    public ResponseEntity<ApiResponse<Page<RedemptionResponse>>> getRedemptions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long merchantId = partnerMerchantResolver.resolveMerchantId(userId);
        return ResponseEntity.ok(ApiResponse.success(
                partnerService.getRedemptions(merchantId, page, size)));
    }
}
