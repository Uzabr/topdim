package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.CouponImage;

/**
 * Репозиторий для управления изображениями купонов.
 */
public interface CouponImageRepository extends JpaRepository<CouponImage, Long> {
    void deleteAllByCouponOfferId(Long couponOfferId);
}
