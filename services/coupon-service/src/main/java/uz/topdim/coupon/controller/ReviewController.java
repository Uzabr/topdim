package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.ReviewEligibilityResponse;
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
            @RequestHeader(value = "X-User-Name", required = false, defaultValue = "Пользователь") String userName,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        try {
            Long reviewId = reviewService.createReview(userId, userName, request);
            return ResponseEntity.ok(ApiResponse.success(
                    "Отзыв отправлен на модерацию", reviewId));
        } catch (IllegalStateException e) {
            // Duplicate or not eligible → 409 Conflict
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error(e.getMessage()));
        } catch (IllegalArgumentException e) {
            // Coupon not found → 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(e.getMessage()));
        }
    }

    /** Public: approved reviews for a coupon (guests allowed). */
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

    /** Authenticated: check if current user can leave a review. */
    @GetMapping("/coupon/{couponId}/eligibility")
    public ResponseEntity<ApiResponse<ReviewEligibilityResponse>> getReviewEligibility(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getReviewEligibility(userId, couponId)
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
