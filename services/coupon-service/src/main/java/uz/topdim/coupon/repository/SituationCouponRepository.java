package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.SituationCoupon;
import uz.topdim.coupon.entity.SituationCouponId;
import uz.topdim.coupon.entity.CouponStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий связей ситуация ↔ купон.
 */
public interface SituationCouponRepository extends JpaRepository<SituationCoupon, SituationCouponId> {

    /**
     * Счётчик активных непросроченных купонов по ситуации — одним запросом для всех ситуаций.
     * Возвращает Object[]{situationId, count}. Ситуации без купонов НЕ попадают в результат —
     * вызывающий код обязан дефолтить отсутствующие id в 0 (см. SituationService).
     */
    @Query("""
            SELECT sc.situation.id, COUNT(sc)
            FROM SituationCoupon sc JOIN sc.coupon c
            WHERE c.status = :status
              AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
            GROUP BY sc.situation.id
            """)
    List<Object[]> countActiveCouponsBySituation(@Param("status") CouponStatus status,
                                                  @Param("now") LocalDateTime now);

    /**
     * ID купонов, привязанных к ситуации (для фильтрации каталога).
     */
    @Query("""
            SELECT sc.coupon.id
            FROM SituationCoupon sc
            WHERE sc.situation.id = :situationId
            ORDER BY sc.sortOrder ASC, sc.coupon.id ASC
            """)
    List<Long> findCouponIdsBySituationId(@Param("situationId") Long situationId);

    /**
     * Удалить все привязки для ситуации (атомарная замена набора).
     */
    @Modifying
    @Query("DELETE FROM SituationCoupon sc WHERE sc.situation.id = :situationId")
    void deleteBySituationId(@Param("situationId") Long situationId);

    List<SituationCoupon> findBySituationId(Long situationId);
}
