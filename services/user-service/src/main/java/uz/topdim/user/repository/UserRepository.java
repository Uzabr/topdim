package uz.topdim.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uz.topdim.user.entity.User;

import java.util.Optional;

/**
 * Репозиторий пользователей.
 * Поиск по email и phone, проверка существования.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
}
