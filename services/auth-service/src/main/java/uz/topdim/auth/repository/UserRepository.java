package uz.topdim.auth.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;

import java.util.Optional;

/**
 * Репозиторий пользователей.
 * Поиск по email и phone, проверка существования.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    Page<User> findByRole(Role role, Pageable pageable);
}
