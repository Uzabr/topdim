package uz.topdim.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.identity.entity.Staff;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    /** Все сотрудники владельца */
    List<Staff> findByUserId(Long userId);

    /** Конкретный сотрудник владельца */
    Optional<Staff> findByUserIdAndId(Long userId, Long id);

    /** Поиск по login user id (для авторизованных кассиров) */
    Optional<Staff> findByLoginUserId(Long loginUserId);

    /** Активные сотрудники владельца */
    List<Staff> findByUserIdAndActiveTrue(Long userId);

    /** Проверка существования login user */
    boolean existsByLoginUserId(Long loginUserId);

    @Query("""
            select distinct staff.merchantLocationId
            from Staff staff
            where staff.merchantId = :merchantId
              and staff.active = true
              and staff.merchantLocationId is not null
            """)
    Set<Long> findActiveLocationIdsByMerchantId(@Param("merchantId") Long merchantId);
}
