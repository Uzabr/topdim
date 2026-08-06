package uz.topdim.coupon.dto;

public record CouponReviewSummary(
        Long couponId,
        Double averageRating,
        Long reviewCount
) {
}
