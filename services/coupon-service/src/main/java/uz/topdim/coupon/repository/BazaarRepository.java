package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.Bazaar;
import uz.topdim.coupon.entity.BazaarStatus;

import java.util.List;

public interface BazaarRepository extends JpaRepository<Bazaar, Long> {

    Page<Bazaar> findByStatus(BazaarStatus status, Pageable pageable);

    @Query("SELECT b FROM Bazaar b WHERE b.status = :status " +
           "AND (LOWER(b.name) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(b.nameUz) LIKE LOWER(CONCAT('%', :query, '%')))")
    Page<Bazaar> searchByName(@Param("query") String query,
                              @Param("status") BazaarStatus status,
                              Pageable pageable);

    @Query("SELECT b FROM Bazaar b WHERE b.status = :status AND b.type = :type")
    Page<Bazaar> findByTypeAndStatus(@Param("type") String type,
                                     @Param("status") BazaarStatus status,
                                     Pageable pageable);

    @Query("SELECT b FROM Bazaar b WHERE b.status = 'ACTIVE' " +
           "AND b.latitude BETWEEN :minLat AND :maxLat " +
           "AND b.longitude BETWEEN :minLon AND :maxLon")
    List<Bazaar> findInBoundingBox(@Param("minLat") double minLat,
                                   @Param("maxLat") double maxLat,
                                   @Param("minLon") double minLon,
                                   @Param("maxLon") double maxLon);
}
