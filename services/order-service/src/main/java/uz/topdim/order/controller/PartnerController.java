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

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/**
 * Контроллер партнёра — погашение купонов, статистика и история.
 * Использует PartnerAccessResolver для определения роли (OWNER/MANAGER/CASHIER)
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
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size,
            @RequestParam(required = false) String couponCode,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo
    ) {
        int parsedPage = parseHistoryInteger("page", page);
        int parsedSize = parseHistoryInteger("size", size);
        LocalDate parsedDateFrom = parseHistoryDate("dateFrom", dateFrom);
        LocalDate parsedDateTo = parseHistoryDate("dateTo", dateTo);
        validateRedemptionHistoryQuery(
                parsedPage, parsedSize, couponCode, parsedDateFrom, parsedDateTo);

        PartnerAccessContext ctx = partnerAccessResolver.resolve(userId);
        Long staffId = resolveHistoryStaffScope(ctx);
        return ResponseEntity.ok(ApiResponse.success(
                partnerService.getRedemptions(
                        ctx.getMerchantId(), staffId, couponCode,
                        parsedDateFrom, parsedDateTo, parsedPage, parsedSize)));
    }

    private int parseHistoryInteger(String field, String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " должен быть целым числом");
        }
    }

    private LocalDate parseHistoryDate(String field, String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    field + " должен быть датой в формате YYYY-MM-DD");
        }
    }

    private void validateRedemptionHistoryQuery(
            int page, int size, String couponCode, LocalDate dateFrom, LocalDate dateTo) {
        if (page < 0) {
            throw new IllegalArgumentException("Номер страницы не может быть отрицательным");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Размер страницы должен быть от 1 до 100");
        }
        if (couponCode != null && couponCode.trim().length() > 50) {
            throw new IllegalArgumentException("Код купона не должен превышать 50 символов");
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new IllegalArgumentException("Начало периода не может быть позже окончания");
        }
    }

    private Long resolveHistoryStaffScope(PartnerAccessContext ctx) {
        if ("OWNER".equals(ctx.getRole()) || "MANAGER".equals(ctx.getRole())) {
            return null;
        }
        if ("CASHIER".equals(ctx.getRole())) {
            if (ctx.getStaffId() == null) {
                throw new IllegalStateException("Контекст кассира не содержит staffId");
            }
            return ctx.getStaffId();
        }
        throw new IllegalStateException("История погашений недоступна для роли " + ctx.getRole());
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
