package uz.topdim.identity.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Сущность refresh токена.
 * Связана с User, имеет срок действия и флаг revoked.
 * При logout токен отзывается (revoked = true).
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;
}
