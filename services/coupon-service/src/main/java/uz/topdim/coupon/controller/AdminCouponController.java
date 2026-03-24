package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<Page<CouponOfferResponse>>> getAllCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponOfferService.getAllForAdmin(page, size)));
    }

    @PostMapping("/coupons")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> createCoupon(
            @Valid @RequestBody CreateCouponOfferRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Купон создан", couponOfferService.create(request)));
    }

    @PatchMapping("/coupons/{id}/status")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> updateCouponStatus(
            @PathVariable Long id,
            @RequestParam CouponStatus status
    ) {
        return ResponseEntity.ok(ApiResponse.success("Статус обновлён", couponOfferService.updateStatus(id, status)));
    }

    @DeleteMapping("/coupons/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteCoupon(@PathVariable Long id) {
        couponOfferService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Купон удалён", null));
    }

    // ==================== Merchants ====================

    @GetMapping("/merchants")
    public ResponseEntity<ApiResponse<List<MerchantResponse>>> getAllMerchants() {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getAllMerchants()));
    }

    @GetMapping("/merchants/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> getMerchant(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(merchantService.getMerchantById(id)));
    }

    @PostMapping("/merchants")
    public ResponseEntity<ApiResponse<MerchantResponse>> createMerchant(
            @Valid @RequestBody CreateMerchantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Партнёр создан", merchantService.createMerchant(request)));
    }

    @PutMapping("/merchants/{id}")
    public ResponseEntity<ApiResponse<MerchantResponse>> updateMerchant(
            @PathVariable Long id,
            @Valid @RequestBody CreateMerchantRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success("Партнёр обновлён", merchantService.updateMerchant(id, request)));
    }
}
