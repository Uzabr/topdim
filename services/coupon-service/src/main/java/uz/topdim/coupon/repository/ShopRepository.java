package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.Shop;
import uz.topdim.coupon.entity.ShopStatus;

import java.util.List;

public interface ShopRepository extends JpaRepository<Shop, Long> {

    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    List<Shop> findByBazaarIdAndStatus(Long bazaarId, ShopStatus status);

    @Query("SELECT s FROM Shop s WHERE s.status = :status " +
           "AND (LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(s.category) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(s.goodsDescription) LIKE LOWER(CONCAT('%', :q, '%')))")
    Page<Shop> search(@Param("q") String query,
                      @Param("status") ShopStatus status,
                      Pageable pageable);

    @Query("SELECT s FROM Shop s WHERE s.bazaar.id = :bazaarId AND s.status = :status " +
           "AND (LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "OR LOWER(s.category) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Shop> searchInBazaar(@Param("bazaarId") Long bazaarId,
                              @Param("q") String query,
                              @Param("status") ShopStatus status);

    @Query("SELECT s FROM Shop s WHERE s.status = 'ACTIVE' " +
           "AND s.latitude BETWEEN :minLat AND :maxLat " +
           "AND s.longitude BETWEEN :minLon AND :maxLon")
    List<Shop> findInBoundingBox(@Param("minLat") double minLat,
                                 @Param("maxLat") double maxLat,
                                 @Param("minLon") double minLon,
                                 @Param("maxLon") double maxLon);

    int countByBazaarIdAndStatus(Long bazaarId, ShopStatus status);
}
