package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.CouponImage;

import java.util.Collection;
import java.util.List;

/**
 * Репозиторий для управления изображениями купонов.
 */
public interface CouponImageRepository extends JpaRepository<CouponImage, Long> {
    void deleteAllByCouponOfferId(Long couponOfferId);
    List<CouponImage> findByCouponOfferIdInOrderBySortOrderAscIdAsc(Collection<Long> couponOfferIds);
}
