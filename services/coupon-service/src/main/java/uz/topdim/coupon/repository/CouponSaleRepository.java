package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.CouponSale;

import java.util.Optional;

/**
 * Репозиторий для idempotency-ledger продаж купонов.
 */
public interface CouponSaleRepository extends JpaRepository<CouponSale, Long> {

    Optional<CouponSale> findByOrderIdAndCouponOfferIdAndCouponOptionId(
            Long orderId, Long couponOfferId, Long couponOptionId);
}
