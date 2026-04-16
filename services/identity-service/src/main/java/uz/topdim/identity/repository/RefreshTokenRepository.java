package uz.topdim.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import uz.topdim.identity.entity.RefreshToken;
import uz.topdim.identity.entity.User;

import java.util.Optional;

/**
 * Репозиторий refresh токенов.
 * Поиск по значению токена, массовый отзыв по userId.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @Query("UPDATE RefreshToken rt SET rt.revoked = true WHERE rt.user = :user")
    void revokeAllByUser(User user);
}
