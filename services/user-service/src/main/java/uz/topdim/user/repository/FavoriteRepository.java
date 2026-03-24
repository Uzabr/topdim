package uz.topdim.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.user.entity.Favorite;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий избранного.
 * Поиск по userId, проверка дубликатов.
 */
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Favorite> findByUserIdAndCouponOfferId(Long userId, Long couponOfferId);

    boolean existsByUserIdAndCouponOfferId(Long userId, Long couponOfferId);

    void deleteByUserIdAndCouponOfferId(Long userId, Long couponOfferId);
}
