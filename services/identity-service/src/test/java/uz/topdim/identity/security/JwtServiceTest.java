package uz.topdim.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import io.jsonwebtoken.ExpiredJwtException;
import org.springframework.test.util.ReflectionTestUtils;
import uz.topdim.identity.entity.Role;
import uz.topdim.identity.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "dG9wZGltLXNlY3JldC1rZXktZm9yLWp3dC10b2tlbi1zaWduaW5nLTI1Ni1iaXQ=";

    private JwtService createJwtService(long accessExpirationMs) {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(jwtService, "accessTokenExpiration", accessExpirationMs);
        ReflectionTestUtils.setField(jwtService, "refreshTokenExpiration", 604_800_000L);
        return jwtService;
    }

    private User createUser() {
        return User.builder()
                .id(77L)
                .email("user@topdim.uz")
                .phone("+998901234567")
                .role(Role.ADMIN)
                .securityVersion(5L)
                .build();
    }

    @Test
    @DisplayName("generateAccessToken: записывает userId в sub и security claims в JWT")
    void generateAccessToken_containsExpectedClaims() {
        JwtService jwtService = createJwtService(60_000L);

        String token = jwtService.generateAccessToken(createUser());

        assertThat(jwtService.extractSubject(token)).isEqualTo("77");
        assertThat(jwtService.extractUserId(token)).isEqualTo(77L);
        assertThat(jwtService.extractEmail(token)).isEqualTo("user@topdim.uz");
        assertThat(jwtService.extractRole(token)).isEqualTo("ADMIN");
        assertThat(jwtService.extractSecurityVersion(token)).isEqualTo(5L);
        assertThat(jwtService.extractJti(token)).isNotBlank();
        assertThat(jwtService.getRemainingExpiration(token)).isPositive();
        assertThat(jwtService.isTokenExpired(token)).isFalse();
    }

    @Test
    @DisplayName("expired token: попытка прочитать claims приводит к ExpiredJwtException")
    void expiredToken_readingClaimsThrowsExpiredJwtException() {
        JwtService jwtService = createJwtService(-1_000L);

        String token = jwtService.generateAccessToken(createUser());

        assertThatThrownBy(() -> jwtService.extractUserId(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
