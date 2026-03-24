package uz.topdim.coupon.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.coupon.entity.Merchant;

import java.util.List;

/**
 * Репозиторий партнёров.
 * Поиск по имени, статусу.
 */
public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    List<Merchant> findByActiveTrue();
}
