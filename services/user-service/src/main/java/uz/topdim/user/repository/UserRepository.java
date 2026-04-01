package uz.topdim.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.user.entity.User;

import java.util.Optional;

/**
 * Репозиторий пользователей.
 * Поиск по email и phone, проверка существования.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Page<User> findByRole(String role, Pageable pageable);

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchByEmailOrName(@Param("search") String search, Pageable pageable);
}

