package uz.topdim.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.Redemption;

import java.util.Optional;

public interface RedemptionRepository extends JpaRepository<Redemption, Long> {
    Optional<Redemption> findByRedemptionCode(String code);
    Optional<Redemption> findByPurchasedCouponId(Long purchasedCouponId);
}
