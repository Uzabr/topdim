package uz.topdim.order.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.order.client.PartnerAccessContext;
import uz.topdim.order.dto.CreateRedemptionRequest;
import uz.topdim.order.dto.PartnerDashboardResponse;
import uz.topdim.order.dto.PartnerStatsResponse;
import uz.topdim.order.dto.QrRedemptionRequest;
import uz.topdim.order.dto.RedeemCouponResponse;
import uz.topdim.order.dto.RedemptionResponse;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.service.OrderService;
import uz.topdim.order.service.PartnerAccessResolver;
import uz.topdim.order.service.PartnerMerchantResolver;
import uz.topdim.order.service.PartnerService;

/**
 * Контроллер партнёра — погашение купонов, статистика и история.
 * Использует PartnerAccessResolver для определения роли (OWNER/CASHIER)
 * и привязки к филиалу.
 */
@RestController
@RequestMapping("/api/v1/partner")
@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;
    private final OrderService orderService;
    private final PartnerMerchantResolver partnerMerchantResolver;
    private final PartnerAccessResolver partnerAccessResolver;

    /** Погашение купона по PIN-коду. */
    @PostMapping("/redemptions")
    public ResponseEntity<ApiResponse<RedeemCouponResponse>> createRedemption(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateRedemptionRequest request
    ) {
        PartnerAccessContext ctx = partnerAccessResolver.resolveForRedemption(userId);
        String couponCode = request.getCouponCode().trim().toUpperCase();
        String staffName = ctx.getStaffName() != null ? ctx.getStaffName() : request.getStaffName();
        PurchasedCoupon coupon = orderService.redeemCoupon(
                couponCode, ctx.getMerchantId(), staffName,
                ctx.getMerchantLocationId(), ctx.getStaffId(), "PIN");
        return ResponseEntity.ok(ApiResponse.success("Купон использован", orderService.mapToRedeemResponse(coupon)));
    }

    /** Погашение купона по QR-токену (сканирование). */
    @PostMapping("/redemptions/qr")
    public ResponseEntity<ApiResponse<RedeemCouponResponse>> redeemByQr(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody QrRedemptionRequest request
    ) {
        PartnerAccessContext ctx = partnerAccessResolver.resolveForRedemption(userId);
        String staffName = ctx.getStaffName() != null ? ctx.getStaffName() : request.getStaffName();
        PurchasedCoupon coupon = orderService.redeemByQrToken(
                request.getQrToken().trim(), ctx.getMerchantId(), staffName,
                ctx.getMerchantLocationId(), ctx.getStaffId());
        return ResponseEntity.ok(ApiResponse.success("Купон использован по QR", orderService.mapToRedeemResponse(coupon)));
    }

    /** Статистика продаж партнёра. */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<PartnerStatsResponse>> getStats(
            @RequestHeader("X-User-Id") Long userId
    ) {
        PartnerAccessContext ctx = partnerAccessResolver.resolveForDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success(partnerService.getStats(ctx.getMerchantId())));
    }

    /** История погашений. */
    @GetMapping("/redemptions")
    public ResponseEntity<ApiResponse<Page<RedemptionResponse>>> getRedemptions(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PartnerAccessContext ctx = partnerAccessResolver.resolve(userId);
        // Cashier sees only own redemptions, Owner/Manager sees all merchant redemptions
        if ("CASHIER".equals(ctx.getRole()) && ctx.getStaffId() != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    partnerService.getRedemptionsByStaff(ctx.getMerchantId(), ctx.getStaffId(), page, size)));
        }
        return ResponseEntity.ok(ApiResponse.success(
                partnerService.getRedemptions(ctx.getMerchantId(), page, size)));
    }

    /** Дашборд партнёра — KPI, последние погашения. Owner/Manager only. */
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<PartnerDashboardResponse>> getDashboard(
            @RequestHeader("X-User-Id") Long userId
    ) {
        PartnerAccessContext ctx = partnerAccessResolver.resolveForDashboard(userId);
        return ResponseEntity.ok(ApiResponse.success(partnerService.getDashboard(ctx.getMerchantId())));
    }
}
