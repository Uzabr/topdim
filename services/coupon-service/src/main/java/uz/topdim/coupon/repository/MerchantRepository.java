package uz.topdim.coupon.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    // Admin paginated search
    @Query("""
            SELECT m FROM Merchant m
            WHERE (:search IS NULL OR :search = '' OR (
                LOWER(m.name) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%'))
                OR (m.contactPerson IS NOT NULL AND LOWER(m.contactPerson) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
                OR (m.email IS NOT NULL AND LOWER(m.email) LIKE LOWER(CONCAT('%', CAST(:search AS string), '%')))
            ))
            AND (:active IS NULL OR m.active = :active)
            ORDER BY m.id DESC
            """)
    Page<Merchant> searchMerchants(
            @Param("search") String search,
            @Param("active") Boolean active,
            Pageable pageable
    );
}
