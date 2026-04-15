package uz.topdim.identity.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uz.topdim.identity.entity.AuthActionToken;
import uz.topdim.identity.entity.AuthActionType;

import java.util.List;
import java.util.Optional;

public interface AuthActionTokenRepository extends JpaRepository<AuthActionToken, Long> {

    /** Найти по hash токена и типу (для confirm). */
    Optional<AuthActionToken> findByTokenHashAndType(String tokenHash, AuthActionType type);

    /** Все активные (не used, не revoked) токены пользователя по типу. */
    @Query("SELECT t FROM AuthActionToken t WHERE t.userId = :userId AND t.type = :type " +
            "AND t.usedAt IS NULL AND t.revoked = false ORDER BY t.createdAt DESC")
    List<AuthActionToken> findActiveByUserIdAndType(
            @Param("userId") Long userId,
            @Param("type") AuthActionType type);

    /** Revoke все активные токены пользователя по типу (при создании нового). */
    @Modifying
    @Query("UPDATE AuthActionToken t SET t.revoked = true " +
            "WHERE t.userId = :userId AND t.type = :type AND t.usedAt IS NULL AND t.revoked = false")
    void revokeAllActiveByUserIdAndType(
            @Param("userId") Long userId,
            @Param("type") AuthActionType type);
}
