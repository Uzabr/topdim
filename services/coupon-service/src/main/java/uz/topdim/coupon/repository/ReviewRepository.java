package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.Review;
import uz.topdim.coupon.entity.ReviewStatus;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Page<Review> findByStatusOrderByCreatedAtDesc(ReviewStatus status, Pageable pageable);
    Page<Review> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    Page<Review> findByCouponOfferIdAndStatusOrderByCreatedAtDesc(Long couponOfferId, ReviewStatus status, Pageable pageable);
}
