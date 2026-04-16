package uz.topdim.coupon.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.topdim.coupon.dto.CreateReviewRequest;
import uz.topdim.coupon.dto.ReviewResponse;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.Review;
import uz.topdim.coupon.entity.ReviewStatus;
import uz.topdim.coupon.repository.CouponOfferRepository;
import uz.topdim.coupon.repository.ReviewRepository;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final CouponOfferRepository couponOfferRepository;

    @Transactional
    public Long createReview(Long userId, String userName, CreateReviewRequest request) {
        CouponOffer coupon = couponOfferRepository.findById(request.getCouponOfferId())
                .orElseThrow(() -> new RuntimeException("Купон не найден"));

        Review review = Review.builder()
                .userId(userId)
                .userName(userName)
                .couponOffer(coupon)
                .rating(request.getRating())
                .comment(request.getComment())
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
