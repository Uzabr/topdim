package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.client.OrderReviewEligibilityClient;
import uz.topdim.coupon.client.ReviewEligibilityResponse;
import uz.topdim.coupon.dto.CreateReviewRequest;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.Review;
import uz.topdim.coupon.entity.ReviewStatus;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.ReviewRepository;

import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final CouponOfferRepository couponOfferRepository;
    private final OrderReviewEligibilityClient orderReviewEligibilityClient;

    /**
     * Create or resubmit a review with full business rule enforcement.
     *
     * Rules:
     * 1. Coupon must exist.
     * 2. User must have a USED purchased coupon (checked via order-service).
     * 3. If user has PENDING or APPROVED review → block (HTTP 409).
     * 4. If user has REJECTED review → resubmit (update same record to PENDING).
     * 5. Otherwise → create new PENDING review.
     */
    @Transactional
    public Long createReview(Long userId, String userName, CreateReviewRequest request) {
        Long couponOfferId = request.getCouponOfferId();

        // 1. Verify coupon exists
        CouponOffer coupon = couponOfferRepository.findById(couponOfferId)
                .orElseThrow(() -> new IllegalArgumentException("Купон не найден"));

        // 2. Check for existing PENDING or APPROVED review → block duplicate
        if (reviewRepository.existsByUserIdAndCouponOfferIdAndStatusIn(
                userId, couponOfferId, Set.of(ReviewStatus.PENDING, ReviewStatus.APPROVED))) {
            throw new IllegalStateException("Вы уже оставили отзыв по этому купону");
        }

        // 3. Check eligibility via order-service
        try {
            ApiResponse<ReviewEligibilityResponse> eligibilityRes =
                    orderReviewEligibilityClient.getReviewEligibility(userId, couponOfferId);
            ReviewEligibilityResponse eligibility = eligibilityRes.getData();
            if (eligibility == null || !eligibility.isEligible()) {
                throw new IllegalStateException("Оставить отзыв можно после использования купона");
            }
        } catch (IllegalStateException e) {
            throw e; // re-throw business rule exceptions
        } catch (Exception e) {
            log.error("Ошибка проверки eligibility для userId={} couponOfferId={}: {}",
                    userId, couponOfferId, e.getMessage());
            throw new IllegalStateException("Оставить отзыв можно после использования купона");
        }

        // 4. Check for REJECTED review → resubmit
        var existingReview = reviewRepository
                .findFirstByUserIdAndCouponOfferIdOrderByCreatedAtDesc(userId, couponOfferId);

        if (existingReview.isPresent() && existingReview.get().getStatus() == ReviewStatus.REJECTED) {
            Review rejected = existingReview.get();
            rejected.setRating(request.getRating());
            rejected.setComment(request.getComment().trim());
            rejected.setUserName(userName);
            rejected.setStatus(ReviewStatus.PENDING);
            rejected.setRejectReason(null);
            reviewRepository.save(rejected);
            return rejected.getId();
        }

        // 5. Create new PENDING review
        Review review = Review.builder()
                .userId(userId)
                .userName(userName)
                .couponOffer(coupon)
                .rating(request.getRating())
                .comment(request.getComment().trim())
                .status(ReviewStatus.PENDING)
                .build();

        reviewRepository.save(review);
        return review.getId();
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getMyReviews(Long userId, Pageable pageable) {
        return reviewRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<ReviewResponse> getApprovedReviewsForCoupon(Long couponId, Pageable pageable) {
        return reviewRepository.findByCouponOfferIdAndStatusOrderByCreatedAtDesc(
                couponId, ReviewStatus.APPROVED, pageable).map(this::mapToResponse);
    }

    /**
     * Check review eligibility for frontend (proxies to order-service).
     */
    @Transactional(readOnly = true)
    public ReviewEligibilityResponse getReviewEligibility(Long userId, Long couponOfferId) {
        // Verify coupon exists
        couponOfferRepository.findById(couponOfferId)
                .orElseThrow(() -> new IllegalArgumentException("Купон не найден"));

        try {
            ApiResponse<ReviewEligibilityResponse> res =
                    orderReviewEligibilityClient.getReviewEligibility(userId, couponOfferId);
            return res.getData();
        } catch (Exception e) {
            log.error("Ошибка проверки eligibility: {}", e.getMessage());
            return new ReviewEligibilityResponse(); // eligible=false by default
        }
    }

    private ReviewResponse mapToResponse(Review review) {
        return ReviewResponse.builder()
                .id(review.getId())
                .userId(review.getUserId())
                .userName(review.getUserName())
                .couponOfferId(review.getCouponOffer().getId())
                .rating(review.getRating())
                .comment(review.getComment())
                .status(review.getStatus())
                .rejectReason(review.getRejectReason())
                .createdAt(review.getCreatedAt())
                .build();
    }
}
