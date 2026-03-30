package uz.topdim.bazaar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.bazaar.entity.Shop;

import java.util.List;

/**
 * Репозиторий магазинов.
 * Поиск по bazaarId, категории, тегам.
 */
public interface ShopRepository extends JpaRepository<Shop, Long> {
    List<Shop> findByBazaarIdAndActiveTrue(Long bazaarId);
    List<Shop> findByBazaarIdAndHasCouponTrueAndActiveTrue(Long bazaarId);

    @Query("SELECT s FROM Shop s LEFT JOIN s.productTags t WHERE s.active = true AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.goodsDescription) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(t.tag) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<Shop> search(@Param("q") String query);

    List<Shop> findByCategoryIdAndActiveTrue(Long categoryId);

    List<Shop> findByUserId(Long userId);

    java.util.Optional<Shop> findByUserIdAndId(Long userId, Long shopId);
}
