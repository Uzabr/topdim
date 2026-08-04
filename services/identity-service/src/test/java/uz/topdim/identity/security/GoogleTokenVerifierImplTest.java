package uz.topdim.identity.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.topdim.identity.dto.GoogleIdentity;
import uz.topdim.identity.exception.AuthException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Криптопроверка Google ID-token без сети: локальная RSA-пара + ин-мемори
 * {@link GooglePublicKeyProvider}. Токены подписываются jjwt тем же приватным ключом,
 * что резолвится провайдером по {@code kid} — так проверяем реальную RS256-подпись.
 *
 * <p>Негативные кейсы (безопасность): неверный aud, неверный iss, просрочен, битая подпись
 * (подписан ДРУГИМ ключом), неизвестный kid, client-id не задан.
 */
class GoogleTokenVerifierImplTest {

    private static final String CLIENT_ID = "topdim-web.apps.googleusercontent.com";
    private static final String KID = "test-kid-1";
    private static final String GOOGLE_ISS = "https://accounts.google.com";

    private static KeyPair googleKeyPair;   // "настоящий" ключ Google (провайдер вернёт его публичную часть)
    private static KeyPair attackerKeyPair; // чужой ключ — для теста битой подписи

    /** Провайдер знает единственный публичный ключ Google по {@link #KID}; иначе — null. */
    private final GooglePublicKeyProvider provider =
            kid -> KID.equals(kid) ? googleKeyPair.getPublic() : null;

    private final GoogleTokenVerifierImpl verifier =
            new GoogleTokenVerifierImpl(provider, CLIENT_ID);

    @BeforeAll
    static void generateKeys() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        googleKeyPair = gen.generateKeyPair();
        attackerKeyPair = gen.generateKeyPair();
    }

    // ---- happy path ----

    @Test
    @DisplayName("валидный токен → GoogleIdentity с ожидаемыми sub/email/emailVerified")
    void validToken_returnsIdentity() {
        String token = token(KID, GOOGLE_ISS, CLIENT_ID,
                Instant.now().plusSeconds(300), googleKeyPair.getPrivate());

        GoogleIdentity identity = verifier.verify(token);

        assertThat(identity.sub()).isEqualTo("google-sub-123");
        assertThat(identity.email()).isEqualTo("user@example.com");
        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    @DisplayName("issuer без схемы (accounts.google.com) тоже валиден")
    void issuerWithoutScheme_isAccepted() {
        String token = token(KID, "accounts.google.com", CLIENT_ID,
                Instant.now().plusSeconds(300), googleKeyPair.getPrivate());

        assertThat(verifier.verify(token).sub()).isEqualTo("google-sub-123");
    }

    // ---- негативные кейсы ----

    @Test
    @DisplayName("неверный aud (чужой client-id) → AuthException")
    void wrongAudience_throws() {
        String token = token(KID, GOOGLE_ISS, "someone-else.apps.googleusercontent.com",
                Instant.now().plusSeconds(300), googleKeyPair.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("неверный iss → AuthException")
    void wrongIssuer_throws() {
        String token = token(KID, "https://evil.example.com", CLIENT_ID,
                Instant.now().plusSeconds(300), googleKeyPair.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("просроченный токен (exp в прошлом) → AuthException")
    void expiredToken_throws() {
        String token = token(KID, GOOGLE_ISS, CLIENT_ID,
                Instant.now().minusSeconds(60), googleKeyPair.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("битая подпись (подписан ЧУЖИМ ключом, провайдер вернёт правильный публичный) → AuthException")
    void tamperedSignature_throws() {
        String token = token(KID, GOOGLE_ISS, CLIENT_ID,
                Instant.now().plusSeconds(300), attackerKeyPair.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("неизвестный kid (провайдер вернул null) → AuthException")
    void unknownKid_throws() {
        String token = token("rotated-away-kid", GOOGLE_ISS, CLIENT_ID,
                Instant.now().plusSeconds(300), googleKeyPair.getPrivate());

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("client-id не задан → «Google-вход не настроен» (токен не принимается молча)")
    void notConfigured_throws() {
        GoogleTokenVerifierImpl unconfigured = new GoogleTokenVerifierImpl(provider, "");
        String token = token(KID, GOOGLE_ISS, CLIENT_ID,
                Instant.now().plusSeconds(300), googleKeyPair.getPrivate());

        assertThatThrownBy(() -> unconfigured.verify(token))
                .isInstanceOf(AuthException.class)
                .hasMessage("Google-вход не настроен");
    }

    // ---- algorithm-confusion (постоянные регресс-тесты) ----

    @Test
    @DisplayName("alg:none — unsecured JWT (без подписи), но валидные iss/aud/exp → AuthException")
    void unsecuredNoneAlg_throws() {
        // Токен без подписи: заголовок alg=none. Верификатор зовёт parseSignedClaims(...),
        // который требует именно подписанный JWS → unsecured отвергается.
        String token = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(GOOGLE_ISS)
                .audience().add(CLIENT_ID).and()
                .subject("google-sub-123")
                .claim("email", "user@example.com")
                .claim("email_verified", true)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .compact(); // без signWith → unsecured (alg=none)

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("HS256-confusion — подписан HMAC на байтах ПУБЛИЧНОГО RSA-ключа Google → AuthException")
    void hs256KeyConfusion_throws() {
        // Классическая атака: злоумышленник подписывает токен HS256, взяв за HMAC-секрет
        // байты публичного RSA-ключа Google (он общедоступен). Провайдер по kid отдаёт тот же
        // публичный ключ. Верификатор обязан выдавать ключ ТОЛЬКО для RS256 → HS256 отвергается.
        String token = Jwts.builder()
                .header().keyId(KID).and()
                .issuer(GOOGLE_ISS)
                .audience().add(CLIENT_ID).and()
                .subject("google-sub-123")
                .claim("email", "user@example.com")
                .claim("email_verified", true)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(Instant.now().plusSeconds(300)))
                .signWith(Keys.hmacShaKeyFor(googleKeyPair.getPublic().getEncoded()), Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> verifier.verify(token))
                .isInstanceOf(AuthException.class);
    }

    // ---- helper ----

    private static String token(String kid, String issuer, String audience,
                                Instant expiry, PrivateKey signingKey) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .issuer(issuer)
                .audience().add(audience).and()
                .subject("google-sub-123")
                .claim("email", "user@example.com")
                .claim("email_verified", true)
                .issuedAt(Date.from(Instant.now().minusSeconds(5)))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.RS256)
                .compact();
    }
}
