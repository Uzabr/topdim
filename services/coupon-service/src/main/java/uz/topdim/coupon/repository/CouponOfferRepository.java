package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;
import uz.topdim.coupon.dto.CouponAssigneeResponse;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий купонных предложений.
 * Поиск с фильтрами по категории, статусу, поиску.
 */
public interface CouponOfferRepository extends JpaRepository<CouponOffer, Long>,
        JpaSpecificationExecutor<CouponOffer> {

    @Override
    @EntityGraph(attributePaths = {"merchant", "category"})
    Page<CouponOffer> findAll(Specification<CouponOffer> specification, Pageable pageable);

    Page<CouponOffer> findByStatus(CouponStatus status, Pageable pageable);

    Page<CouponOffer> findAllByStatus(CouponStatus status, Pageable pageable);

    Page<CouponOffer> findByStatusAndCategoryId(CouponStatus status, Long categoryId, Pageable pageable);

    @Query("SELECT c FROM CouponOffer c WHERE c.status = :status AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.offerDescription) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CouponOffer> searchByTitleOrDescription(@Param("status") CouponStatus status,
                                                  @Param("search") String search,
                                                  Pageable pageable);

    @Query("SELECT c FROM CouponOffer c WHERE c.status = 'ACTIVE' ORDER BY c.totalSold DESC")
    List<CouponOffer> findTopSelling(Pageable pageable);

    @Query("""
            SELECT c FROM CouponOffer c
             WHERE c.status = :status
               AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
            """)
    Page<CouponOffer> findPublicByStatus(@Param("status") CouponStatus status,
                                          @Param("now") java.time.LocalDateTime now,
                                          Pageable pageable);

    @Query("""
            SELECT c FROM CouponOffer c
             WHERE c.status = :status
               AND c.category.id = :categoryId
               AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
            """)
    Page<CouponOffer> findPublicByStatusAndCategoryId(@Param("status") CouponStatus status,
                                                       @Param("categoryId") Long categoryId,
                                                       @Param("now") java.time.LocalDateTime now,
                                                       Pageable pageable);

    @Query("""
            SELECT c FROM CouponOffer c
             WHERE c.status = :status
               AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
               AND (
                    LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(c.offerDescription) LIKE LOWER(CONCAT('%', :search, '%'))
               )
            """)
    Page<CouponOffer> searchPublicByTitleOrDescription(@Param("status") CouponStatus status,
                                                        @Param("search") String search,
                                                        @Param("now") java.time.LocalDateTime now,
                                                        Pageable pageable);

    @Query("""
            SELECT c FROM CouponOffer c
             WHERE c.status = 'ACTIVE'
               AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
             ORDER BY c.totalSold DESC
            """)
    List<CouponOffer> findPublicTopSelling(@Param("now") java.time.LocalDateTime now,
                                            Pageable pageable);

    @Query(value = """
            SELECT c FROM SituationCoupon sc
            JOIN sc.coupon c
            JOIN sc.situation s
            WHERE s.slug = :slug
              AND s.active = true
              AND c.status = :status
              AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
            """,
            countQuery = """
            SELECT COUNT(c) FROM SituationCoupon sc
            JOIN sc.coupon c
            JOIN sc.situation s
            WHERE s.slug = :slug
              AND s.active = true
              AND c.status = :status
              AND (c.buyUntil IS NULL OR c.buyUntil >= :now)
            """)
    Page<CouponOffer> findPublicBySituationSlug(@Param("status") CouponStatus status,
                                                 @Param("slug") String slug,
                                                 @Param("now") java.time.LocalDateTime now,
                                                 Pageable pageable);

    List<CouponOffer> findByMerchantIdAndStatus(Long merchantId, CouponStatus status);

    Optional<CouponOffer> findByIdAndStatus(Long id, CouponStatus status);

    Page<CouponOffer> findByMerchantId(Long merchantId, Pageable pageable);

    Page<CouponOffer> findByMerchantIdAndStatus(Long merchantId, CouponStatus status, Pageable pageable);

    @Modifying
    @Query("UPDATE CouponOffer c SET c.viewCount = c.viewCount + 1 WHERE c.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CouponOffer c
               SET c.status = uz.topdim.coupon.entity.CouponStatus.DRAFT,
                   c.assignedModeratorId = :moderatorId,
                   c.assignedModeratorName = :moderatorName
             WHERE c.id = :id
               AND c.status = uz.topdim.coupon.entity.CouponStatus.LEAD
            """)
    int claimLead(@Param("id") Long id,
                  @Param("moderatorId") Long moderatorId,
                  @Param("moderatorName") String moderatorName);

    boolean existsByMerchantIdAndStatusIn(Long merchantId, List<CouponStatus> statuses);

    boolean existsByCategoryId(Long categoryId);

    long countByMerchantId(Long merchantId);

    long countByMerchantIdAndStatus(Long merchantId, CouponStatus status);

    @Query("""
            SELECT new uz.topdim.coupon.dto.CouponAssigneeResponse(
                c.assignedModeratorId,
                MAX(c.assignedModeratorName)
            )
            FROM CouponOffer c
            WHERE c.assignedModeratorId IS NOT NULL
            GROUP BY c.assignedModeratorId
            ORDER BY MAX(c.assignedModeratorName), c.assignedModeratorId
            """)
    List<CouponAssigneeResponse> findDistinctAssignees();

    /**
     * Atomic increment totalSold и totalTurnover.
     * Безопасно при конкурентных вызовах — один атомарный UPDATE.
     */
    @Modifying
    @Query("UPDATE CouponOffer o SET o.totalSold = o.totalSold + :qty, " +
           "o.totalTurnover = o.totalTurnover + :amount WHERE o.id = :offerId")
    int atomicIncrementSoldAndTurnover(@Param("offerId") Long offerId,
                                       @Param("qty") int qty,
                                       @Param("amount") java.math.BigDecimal amount);
}
