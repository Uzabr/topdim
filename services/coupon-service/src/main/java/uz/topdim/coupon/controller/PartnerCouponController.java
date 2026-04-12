package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.CreateCouponOfferRequest;
import uz.topdim.coupon.service.PartnerCouponService;

/**
 * Контроллер партнёра — управление СВОИМИ купонами.
 * Требует роль PARTNER.
 */
@RestController
@RequestMapping("/api/v1/partner/coupons")
@PreAuthorize("hasAnyRole('PARTNER', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class PartnerCouponController {

    private final PartnerCouponService partnerCouponService;

    /** Список моих купонов (опционально по статусу). */
    @GetMapping
    public ResponseEntity<ApiResponse<Page<CouponOfferResponse>>> getMyCoupons(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                partnerCouponService.getMyCoupons(userId, status, page, size)));
    }

    /** Детали моего купона. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> getMyCoupon(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                partnerCouponService.getMyCouponById(userId, id)));
    }

    /** Создать купон → статус DRAFT. */
    @PostMapping
    public ResponseEntity<ApiResponse<CouponOfferResponse>> createCoupon(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateCouponOfferRequest request
    ) {
        CouponOfferResponse response = partnerCouponService.createCouponOffer(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Купон сохранён как черновик", response));
    }

    /** Обновить купон (только DRAFT/REVISION_REQUESTED). */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> updateCoupon(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CreateCouponOfferRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Купон обновлён",
                partnerCouponService.updateMyCoupon(userId, id, request)));
    }
}
