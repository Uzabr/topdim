package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.CouponRedemptionLedger;

import java.util.Optional;

public interface CouponRedemptionLedgerRepository extends JpaRepository<CouponRedemptionLedger, Long> {
    Optional<CouponRedemptionLedger> findByPurchasedCouponId(Long purchasedCouponId);
}
