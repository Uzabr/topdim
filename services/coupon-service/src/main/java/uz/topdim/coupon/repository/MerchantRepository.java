package uz.topdim.coupon.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.coupon.entity.Merchant;

import java.util.List;
import java.util.Optional;

/**
 * Репозиторий партнёров.
 * Поиск по имени, статусу.
 */
public interface MerchantRepository extends JpaRepository<Merchant, Long> {
    List<Merchant> findByActiveTrue();

    Optional<Merchant> findByUserId(Long userId);
    Optional<Merchant> findByTelegramChatId(String telegramChatId);

    // Bot lead name lookup
    Optional<Merchant> findFirstByNameIgnoreCase(String name);
    long countByNameIgnoreCase(String name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT merchant FROM Merchant merchant WHERE merchant.id = :id")
    Optional<Merchant> findByIdForUpdate(@Param("id") Long id);

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
