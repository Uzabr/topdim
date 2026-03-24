package uz.topdim.auth.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uz.topdim.auth.entity.User;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * Сервис работы с JWT токенами.
 * Генерация access token (15 мин) и refresh token (7 дней).
 * Валидация и извлечение claims из токена.
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    /**
     * Генерирует JWT access token.
     * Claims: userId (sub), email, role, firstName.
     *
     * @param user пользователь для токена
     * @return подписанный JWT строка
     */
    public String generateAccessToken(User user) {
        return buildToken(user, accessTokenExpiration);
    }

    /** Возвращает TTL access token в миллисекундах. */
    public long getAccessTokenExpiration() {
        return accessTokenExpiration;
    }

    /** Возвращает TTL refresh token в миллисекундах. */
    public long getRefreshTokenExpiration() {
        return refreshTokenExpiration;
    }

    private String buildToken(User user, long expiration) {
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claims(Map.of(
                        "email", user.getEmail(),
                        "role", user.getRole().name(),
                        "firstName", user.getFirstName()
                ))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    /**
     * Извлекает все claims из JWT токена.
     *
     * @param token JWT строка
     * @return Claims объект с данными из токена
     * @throws JwtException если токен невалиден
     */
    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Проверяет валидность JWT токена (подпись + срок действия).
     *
     * @param token JWT строка
     * @return true если токен валиден и не истёк
     */
    public boolean isTokenValid(String token) {
        try {
            Claims claims = extractClaims(token);
            return !claims.getExpiration().before(new Date());
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * Извлекает userId (subject) из JWT.
     *
     * @param token JWT строка
     * @return userId как строка
     */
    public String extractUserId(String token) {
        return extractClaims(token).getSubject();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }
}
