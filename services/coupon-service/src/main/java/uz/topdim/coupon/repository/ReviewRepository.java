package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.Review;
import uz.topdim.coupon.entity.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);
    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Review> findByCouponOfferIdAndStatusOrderByCreatedAtDesc(Long couponOfferId, ReviewStatus status, Pageable pageable);

    @Query("SELECT COALESCE(AVG(r.rating), 0) FROM Review r WHERE r.couponOffer.id = :couponId AND r.status = 'APPROVED'")
    double getAverageRatingByCouponId(@Param("couponId") Long couponId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.couponOffer.id = :couponId AND r.status = 'APPROVED'")
    int countApprovedByCouponId(@Param("couponId") Long couponId);
}
