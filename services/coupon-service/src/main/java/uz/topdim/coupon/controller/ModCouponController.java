package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.dto.StatusUpdateRequest;
import uz.topdim.coupon.service.ModCouponService;

@RestController
@RequestMapping("/api/v1/mod")
@PreAuthorize("hasAnyRole('MODERATOR', 'ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
public class ModCouponController {

    private final ModCouponService modCouponService;

    @GetMapping("/coupons")
    public ResponseEntity<ApiResponse<Page<CouponOfferResponse>>> getPendingCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                modCouponService.getPendingCoupons(PageRequest.of(page, size))
        ));
    }

    @PatchMapping("/coupons/{id}/review")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> reviewCoupon(
            @RequestHeader("X-User-Id") Long modId,
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isBlank()) {
            throw new IllegalArgumentException("Причина служебного решения обязательна");
        }
        modCouponService.reviewCoupon(modId, id, request.getStatus(), reason);
        return ResponseEntity.ok(ApiResponse.success("Решение по купону сохранено", null));
    }

    @GetMapping("/reviews")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getPendingReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                modCouponService.getPendingReviews(PageRequest.of(page, size))
        ));
    }

    @PatchMapping("/reviews/{id}/review")
    public ResponseEntity<ApiResponse<Void>> reviewUserReview(
            @RequestHeader("X-User-Id") Long modId,
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest request
    ) {
        modCouponService.reviewUserReview(modId, id, request.getStatus(), request.getReason());
        return ResponseEntity.ok(ApiResponse.success("Решение по отзыву сохранено", null));
    }
}
