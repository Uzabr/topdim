package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.CouponOption;

import java.util.List;
import java.util.Collection;

/**
 * Репозиторий вариантов купонов.
 * CRUD операции для CouponOption.
 */
public interface CouponOptionRepository extends JpaRepository<CouponOption, Long> {
    List<CouponOption> findByCouponOfferId(Long couponOfferId);
    List<CouponOption> findByCouponOfferIdInOrderById(Collection<Long> couponOfferIds);

    /**
     * Atomic increment quantitySold с проверкой лимита.
     * Возвращает количество обновлённых строк:
     * - 1 = успешно инкрементировано
     * - 0 = лимит исчерпан или option не найден
     *
     * Безопасно при конкурентных вызовах — атомарный UPDATE,
     * гарантирует что quantitySold + qty <= quantityLimit.
     */
    @Modifying
    @Query("UPDATE CouponOption o SET o.quantitySold = o.quantitySold + :qty, o.version = o.version + 1 " +
           "WHERE o.id = :optionId " +
           "AND (o.quantityLimit IS NULL OR o.quantityLimit = 0 OR o.quantitySold + :qty <= o.quantityLimit)")
    int atomicIncrementSold(@Param("optionId") Long optionId, @Param("qty") int qty);
}
