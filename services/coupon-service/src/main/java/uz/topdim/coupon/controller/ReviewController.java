package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CreateReviewRequest;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.service.ReviewService;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewService reviewService;

    @PostMapping
    public ResponseEntity<ApiResponse<Long>> createReview(
            @RequestHeader("X-User-Id") Long userId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                "Отзыв отправлен на модерацию",
                reviewService.createReview(userId, request)
        ));
    }

    @GetMapping("/coupon/{couponId}")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getCouponReviews(
            @PathVariable Long couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getApprovedReviewsForCoupon(couponId, PageRequest.of(page, size))
        ));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<Page<ReviewResponse>>> getMyReviews(
            @RequestHeader("X-User-Id") Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getMyReviews(userId, PageRequest.of(page, size))
        ));
    }
}
