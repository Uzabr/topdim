package uz.topdim.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.Redemption;

import java.util.Optional;

/**
 * Репозиторий записей погашения.
 * CRUD операции для Redemption.
 */
public interface RedemptionRepository extends JpaRepository<Redemption, Long> {
    Optional<Redemption> findByRedemptionCode(String code);
    Optional<Redemption> findByPurchasedCouponId(Long purchasedCouponId);

    org.springframework.data.domain.Page<Redemption> findByMerchantId(Long merchantId, org.springframework.data.domain.Pageable pageable);
    long countByMerchantId(Long merchantId);
}
