package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.CouponOption;

import java.util.List;

/**
 * Репозиторий вариантов купонов.
 * CRUD операции для CouponOption.
 */
public interface CouponOptionRepository extends JpaRepository<CouponOption, Long> {
    List<CouponOption> findByCouponOfferId(Long couponOfferId);
}
