package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.Situation;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий ситуаций (подборок).
 */
public interface SituationRepository extends JpaRepository<Situation, Long> {

    List<Situation> findByActiveTrueOrderBySortOrderAsc();

    Optional<Situation> findBySlug(String slug);

    Optional<Situation> findBySlugAndActiveTrue(String slug);

    boolean existsBySlug(String slug);
}
