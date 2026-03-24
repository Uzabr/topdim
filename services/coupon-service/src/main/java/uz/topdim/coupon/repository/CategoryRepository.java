package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.Category;

import java.util.List;

/**
 * Репозиторий категорий.
 * Поиск активных категорий с сортировкой.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByActiveTrueOrderBySortOrder();
}
