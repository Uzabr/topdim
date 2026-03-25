package uz.topdim.auth.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.topdim.auth.entity.Role;
import uz.topdim.auth.entity.User;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() throws Exception {
        jwtService = new JwtService();
        setField(jwtService, "jwtSecret", "dG9wZGltLXNlY3JldC1rZXktZm9yLWp3dC10b2tlbi1zaWduaW5nLTI1Ni1iaXQ=");
        setField(jwtService, "accessTokenExpiration", 900000L);
        setField(jwtService, "refreshTokenExpiration", 604800000L);
    }

    @Test
    @DisplayName("generateAccessToken: содержит userId, email, role")
    void generateAccessToken_containsCorrectClaims() {
        User user = User.builder()
                .id(42L).email("test@topdim.uz").firstName("Иван")
                .role(Role.USER).build();

        String token = jwtService.generateAccessToken(user);

        assertThat(token).isNotBlank();
        Claims claims = jwtService.extractClaims(token);
        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("email", String.class)).isEqualTo("test@topdim.uz");
        assertThat(claims.get("role", String.class)).isEqualTo("USER");
        assertThat(claims.get("firstName", String.class)).isEqualTo("Иван");
    }

    @Test
    @DisplayName("isTokenValid: валидный токен → true")
    void isTokenValid_validToken_returnsTrue() {
        User user = User.builder().id(1L).email("a@b.com").firstName("A").role(Role.USER).build();
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("isTokenValid: невалидный токен → false")
    void isTokenValid_invalidToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("invalid.token.here")).isFalse();
    }

    @Test
    @DisplayName("extractUserId: возвращает корректный ID")
    void extractUserId_returnsCorrectId() {
        User user = User.builder().id(99L).email("x@y.com").firstName("X").role(Role.ADMIN).build();
        String token = jwtService.generateAccessToken(user);

        assertThat(jwtService.extractUserId(token)).isEqualTo("99");
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        Field field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }
}
