package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.CouponOffer;
import uz.topdim.coupon.entity.CouponStatus;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий купонных предложений.
 * Поиск с фильтрами по категории, статусу, поиску.
 */
public interface CouponOfferRepository extends JpaRepository<CouponOffer, Long> {

    Page<CouponOffer> findByStatus(CouponStatus status, Pageable pageable);

    Page<CouponOffer> findByStatusAndCategoryId(CouponStatus status, Long categoryId, Pageable pageable);

    @Query("SELECT c FROM CouponOffer c WHERE c.status = :status AND " +
           "(LOWER(c.title) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.shortDescription) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CouponOffer> searchByTitleOrDescription(@Param("status") CouponStatus status,
                                                  @Param("search") String search,
                                                  Pageable pageable);

    @Query("SELECT c FROM CouponOffer c WHERE c.status = 'ACTIVE' ORDER BY c.totalSold DESC")
    List<CouponOffer> findTopSelling(Pageable pageable);

    List<CouponOffer> findByMerchantIdAndStatus(Long merchantId, CouponStatus status);

    Optional<CouponOffer> findByIdAndStatus(Long id, CouponStatus status);

    Page<CouponOffer> findByMerchantId(Long merchantId, Pageable pageable);

    Page<CouponOffer> findByMerchantIdAndStatus(Long merchantId, CouponStatus status, Pageable pageable);
}
