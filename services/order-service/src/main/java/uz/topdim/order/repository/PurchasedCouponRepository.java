package uz.topdim.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.order.entity.PurchasedCoupon;
import uz.topdim.order.entity.PurchasedCouponStatus;

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

    long countByCouponOfferIdIn(java.util.Collection<Long> couponOfferIds);
    long countByCouponOfferIdInAndStatus(java.util.Collection<Long> couponOfferIds, PurchasedCouponStatus status);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(oi.unitPrice * oi.quantity), 0) FROM OrderItem oi WHERE oi.couponOfferId IN :ids")
    java.math.BigDecimal sumRevenueByCouponOfferIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> couponOfferIds);
}
