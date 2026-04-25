package uz.topdim.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий купленных купонов.
 * Поиск по userId и статусу, по коду купона.
 */
public interface PurchasedCouponRepository extends JpaRepository<PurchasedCoupon, Long> {
    List<PurchasedCoupon> findByUserId(Long userId);
    List<PurchasedCoupon> findByUserIdAndStatus(Long userId, PurchasedCouponStatus status);
    List<PurchasedCoupon> findByOrderId(Long orderId);
    Optional<PurchasedCoupon> findByCouponCode(String couponCode);
    Optional<PurchasedCoupon> findByQrToken(String qrToken);

    @Modifying
    @Query("""
            update PurchasedCoupon pc
               set pc.status = uz.topdim.order.entity.PurchasedCouponStatus.EXPIRED
             where pc.status = uz.topdim.order.entity.PurchasedCouponStatus.ACTIVE
               and pc.expiresAt is not null
               and pc.expiresAt < :now
            """)
    int expireActiveCouponsBefore(@Param("now") LocalDateTime now);

    long countByCouponOfferIdIn(java.util.Collection<Long> couponOfferIds);
    long countByCouponOfferIdInAndStatus(java.util.Collection<Long> couponOfferIds, PurchasedCouponStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(oi.unitPrice * oi.quantity), 0) FROM OrderItem oi WHERE oi.couponOfferId IN :ids")
    java.math.BigDecimal sumRevenueByCouponOfferIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> couponOfferIds);

    // === Merchant-based stats (no client-provided coupon ids) ===

    long countByMerchantId(Long merchantId);

    long countByMerchantIdAndStatus(Long merchantId, PurchasedCouponStatus status);

    @Query("SELECT COUNT(DISTINCT pc.couponOfferId) FROM PurchasedCoupon pc WHERE pc.merchantId = :merchantId")
    long countDistinctCouponOfferIdsByMerchantId(@Param("merchantId") Long merchantId);

    @Query("SELECT COALESCE(SUM(oi.unitPrice * oi.quantity), 0) FROM OrderItem oi WHERE oi.merchantId = :merchantId")
    java.math.BigDecimal sumRevenueByMerchantId(@Param("merchantId") Long merchantId);
}
