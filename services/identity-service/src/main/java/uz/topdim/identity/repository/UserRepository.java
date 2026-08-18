package uz.topdim.identity.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;

import java.util.Optional;
import java.util.List;
import java.util.Set;

/**
 * Репозиторий пользователей (объединённый из auth + user).
 * Поиск по email и phone, проверка существования, поиск по имени.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPhone(String phone);
    Optional<User> findByTelegramChatId(Long telegramChatId);
    Optional<User> findByGoogleSub(String googleSub);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    Page<User> findByRole(Role role, Pageable pageable);
    List<User> findAllByRoleInAndEnabledTrueAndDeletedFalseOrderByFirstNameAscLastNameAsc(
            Set<Role> roles);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "u.phone LIKE CONCAT('%', :search, '%')")
    Page<User> searchByEmailOrName(@Param("search") String search, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role = :role AND (" +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "u.phone LIKE CONCAT('%', :search, '%'))")
    Page<User> searchForAdmin(
            @Param("role") Role role,
            @Param("search") String search,
            Pageable pageable
    );
}
