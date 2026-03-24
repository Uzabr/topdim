package uz.topdim.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.auth.entity.User;

import java.util.Optional;

/**
 * Репозиторий пользователей.
 * Поиск по email и phone, проверка существования.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
}
