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

    java.util.Optional<Merchant> findByUserId(Long userId);
    java.util.Optional<Merchant> findByTelegramChatId(String telegramChatId);

    // Bot lead name lookup
    java.util.Optional<Merchant> findFirstByNameIgnoreCase(String name);
    long countByNameIgnoreCase(String name);
}
