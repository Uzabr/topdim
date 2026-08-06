package uz.topdim.order.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.domain.Specification;
import uz.topdim.order.entity.Redemption;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Репозиторий записей погашения.
 * CRUD операции для Redemption.
 */
public interface RedemptionRepository extends JpaRepository<Redemption, Long>, JpaSpecificationExecutor<Redemption> {
    Optional<Redemption> findByRedemptionCode(String code);
    Optional<Redemption> findByPurchasedCouponId(Long purchasedCouponId);

    @Override
    @EntityGraph(attributePaths = "purchasedCoupon")
    Page<Redemption> findAll(Specification<Redemption> specification, Pageable pageable);

    default Page<Redemption> findHistory(
            Long merchantId,
            Long staffId,
            String couponFragment,
            LocalDateTime fromInclusive,
            LocalDateTime toExclusive,
            Pageable pageable) {
        return findAll(RedemptionSpecifications.history(
                merchantId, staffId, couponFragment, fromInclusive, toExclusive), pageable);
    }

    Page<Redemption> findByMerchantId(Long merchantId, Pageable pageable);
    Page<Redemption> findByMerchantIdAndStaffId(Long merchantId, Long staffId, Pageable pageable);
    Page<Redemption> findByMerchantIdAndMerchantLocationId(Long merchantId, Long merchantLocationId, Pageable pageable);
    long countByMerchantId(Long merchantId);
}
