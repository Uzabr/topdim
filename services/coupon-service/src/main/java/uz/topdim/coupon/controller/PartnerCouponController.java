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
import uz.topdim.coupon.dto.CreatePartnerCouponRequest;
import uz.topdim.coupon.service.PartnerCouponService;

/**
 * Контроллер партнёра — управление заявками на акции и СВОИМИ купонами.
 * Требует роль PARTNER.
 * Кассиры имеют роль PARTNER, но не имеют привязки Merchant.userId —
 * создание/редактирование заявок для них заблокировано на уровне сервиса.
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

    /** Создать заявку на акцию → статус LEAD. Фото необязательны. */
    @PostMapping
    public ResponseEntity<ApiResponse<CouponOfferResponse>> createCouponRequest(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreatePartnerCouponRequest request
    ) {
        CouponOfferResponse response = partnerCouponService.createPartnerRequest(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Заявка на акцию принята. TopDim свяжется с вами для оформления.", response));
    }

    /** Обновить заявку/купон (только LEAD/DRAFT/REVISION_REQUESTED). */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> updateCoupon(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long id,
            @Valid @RequestBody CreatePartnerCouponRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Заявка обновлена",
                partnerCouponService.updateMyCoupon(userId, id, request)));
    }
}
