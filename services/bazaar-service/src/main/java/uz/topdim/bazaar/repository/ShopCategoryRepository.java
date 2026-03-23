package uz.topdim.bazaar.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.bazaar.entity.ShopCategory;
import java.util.List;

public interface ShopCategoryRepository extends JpaRepository<ShopCategory, Long> {
    List<ShopCategory> findByActiveTrue();
}
