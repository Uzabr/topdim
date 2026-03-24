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
    Optional<PurchasedCoupon> findByCouponCode(String couponCode);
    Optional<PurchasedCoupon> findByQrToken(String qrToken);
}
