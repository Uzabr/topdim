package uz.topdim.bazaar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.bazaar.entity.BazaarMap;
import java.util.List;

/**
 * Репозиторий внутренних карт базаров.
 * Поиск карты по bazaarId.
 */
public interface BazaarMapRepository extends JpaRepository<BazaarMap, Long> {
    List<BazaarMap> findByBazaarId(Long bazaarId);
}
