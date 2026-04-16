package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.*;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.service.CouponOfferService;
import uz.topdim.coupon.service.MerchantService;
import java.util.List;

/**
 * REST контроллер администрирования купонов.
 * Endpoints: создание, обновление, удаление купонов (Admin only).
 * Требует роль ADMIN.
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminCouponController {

    private final CouponOfferService couponOfferService;
    private final MerchantService merchantService;

    // ==================== Coupons ====================

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<Page<CouponOfferResponse>>> getAllCoupons(
            @RequestParam(required = false) CouponStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponOfferService.getAllForAdmin(status, page, size)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/coupons")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> createCoupon(
            @Valid @RequestBody CreateCouponOfferRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Купон создан", couponOfferService.create(request)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> getCouponById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponOfferService.getByIdAdmin(id)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> updateCoupon(
            @PathVariable Long id,
            @Valid @RequestBody CreateCouponOfferRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String userRole
    ) {
        return ResponseEntity.ok(ApiResponse.success("Купон обновлён", couponOfferService.update(id, request, userId, userRole)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/coupons/{id}/status")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> updateCouponStatus(
            @PathVariable Long id,
            @RequestParam CouponStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success("Статус обновлён", couponOfferService.updateStatus(id, status)));
    }

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        couponOfferService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Купон удалён", null));
    }

    /** Отправить купон на согласование мерчанту (DRAFT/REVISION_REQUESTED → WAITING_FOR_MERCHANT). */
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/coupons/{id}/send-to-approval")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> sendToApproval(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                "Купон отправлен на согласование", couponOfferService.sendToApproval(id)));
    }

    /** Модератор берёт лид в работу (LEAD → DRAFT). */
    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @PatchMapping("/coupons/{id}/take-to-work")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> takeToWork(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") Long moderatorId,
            @RequestHeader("X-User-Email") String moderatorEmail
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Купон взят в работу", couponOfferService.takeToWork(id, moderatorId, moderatorEmail)));
    }

    // ==================== Merchants ====================

    @PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/merchants")
    public ResponseEntity<ApiResponse<List<MerchantResponse>>> getAllMerchants() {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getAllMerchants()));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @GetMapping("/merchants/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMerchant(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getMerchantById(id)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PostMapping("/merchants")
    public ResponseEntity<ApiResponse<MerchantResponse>> createMerchant(
            @Valid @RequestBody CreateMerchantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Партнёр создан", merchantService.createMerchant(request)));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    @PutMapping("/merchants/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateMerchant(
            @PathVariable Long id,
            @Valid @RequestBody CreateMerchantRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Партнёр обновлён", merchantService.updateMerchant(id, request)));
    }

}
