package uz.topdim.coupon.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.service.CouponOfferService;

import java.util.List;

/**
 * REST контроллер купонов.
 * Публичные endpoints: каталог, детали, топ продаж, категории.
 * Admin endpoints: создание, обновление статуса, удаление.
 */
@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponController {

    private final CouponOfferService couponOfferService;

    @GetMapping
    public ResponseEntity<ApiResponse<Page<CouponOfferResponse>>> getCatalog(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "popular") String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                couponOfferService.getCatalog(categoryId, search, sortBy, page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(couponOfferService.getById(id)));
    }

    @GetMapping("/top-selling")
    public ResponseEntity<ApiResponse<List<CouponOfferResponse>>> getTopSelling(
            @RequestParam(defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponOfferService.getTopSelling(limit)));
    }
}
