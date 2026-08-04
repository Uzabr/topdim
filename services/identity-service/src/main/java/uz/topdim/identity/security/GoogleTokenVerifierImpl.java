package uz.topdim.identity.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.ProtectedHeader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.topdim.identity.dto.GoogleIdentity;
import uz.topdim.identity.exception.AuthException;

import java.security.Key;
import java.util.Set;

/**
 * Реальная криптографическая проверка Google ID-token — без сторонних библиотек
 * (только jjwt 0.12.x + {@link GooglePublicKeyProvider} поверх JDK-HttpClient/Jackson).
 *
 * <p>Проверяется всё, что делает токен доверенным:
 * <ul>
 *   <li>подпись <b>RS256</b> по публичному ключу Google, резолвнутому из JWKS по {@code kid};</li>
 *   <li>{@code iss} ∈ {@code {accounts.google.com, https://accounts.google.com}};</li>
 *   <li>{@code aud} == {@code google.client-id} — токен выписан ИМЕННО нашему приложению
 *       (пропуск этой проверки — типовая дыра: чужой валидно-подписанный токен прошёл бы);</li>
 *   <li>{@code exp} в будущем (проверяет сам jjwt при парсинге).</li>
 * </ul>
 *
 * <p>Паттерн бина — как {@link TelegramLoginVerifier}: безусловный {@code @Component} с
 * {@code @Value(...:"")} по умолчанию (НЕ {@code @ConditionalOnProperty}), иначе конструкторная
 * инъекция {@code GoogleTokenVerifier} в {@code AuthService} сломала бы старт в окружении без
 * {@code google.client-id}. Если client-id не задан — {@code verify()} бросает «не настроено»,
 * НО никогда не доверяет непроверенному токену.
 *
 * <p>Сырое {@link JwtException} наружу не пробрасывается — любая ошибка парсинга/подписи/срока
 * заворачивается в {@link AuthException} (единый контракт слоя аутентификации).
 */
@Slf4j
@Component
public class GoogleTokenVerifierImpl implements GoogleTokenVerifier {

    private static final Set<String> VALID_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");
    private static final String RS256 = "RS256";

    private final GooglePublicKeyProvider keyProvider;
    private final String clientId;
    private final boolean configured;

    public GoogleTokenVerifierImpl(
            GooglePublicKeyProvider keyProvider,
            @Value("${google.client-id:}") String clientId) {
        this.keyProvider = keyProvider;
        this.clientId = clientId == null ? "" : clientId.trim();
        this.configured = !this.clientId.isBlank();
        if (!configured) {
            log.warn("Google login disabled: google.client-id не задан");
        }
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (!configured) {
            throw new AuthException("Google-вход не настроен");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new AuthException("Пустой Google ID-token");
        }
        try {
            // keyLocator: по kid из защищённого заголовка резолвим публичный ключ Google.
            // Требуем именно RS256 (защита от подмены алгоритма) — иначе ключ не выдаём и jjwt отклонит токен.
            Claims claims = Jwts.parser()
                    .keyLocator(header -> resolveKey(header))
                    .build()
                    .parseSignedClaims(idToken) // здесь же проверяются подпись и exp
                    .getPayload();

            verifyIssuer(claims.getIssuer());
            verifyAudience(claims.getAudience());

            return toGoogleIdentity(claims);
        } catch (AuthException e) {
            throw e;
        } catch (JwtException | IllegalArgumentException e) {
            // истёкший / битая подпись / неизвестный kid / некорректный формат — единый ответ, без утечки деталей
            log.warn("Google ID-token отклонён: {}", e.getMessage());
            throw new AuthException("Невалидный Google ID-token");
        }
    }

    private Key resolveKey(io.jsonwebtoken.Header header) {
        if (header instanceof ProtectedHeader protectedHeader
                && RS256.equals(protectedHeader.getAlgorithm())) {
            return keyProvider.resolve(protectedHeader.getKeyId());
        }
        return null;
    }

    private void verifyIssuer(String issuer) {
        if (!VALID_ISSUERS.contains(issuer)) {
            throw new AuthException("Недоверенный issuer Google ID-token");
        }
    }

    private void verifyAudience(Set<String> audience) {
        if (audience == null || !audience.contains(clientId)) {
            // токен выписан другому Google-приложению → отвергаем
            throw new AuthException("Google ID-token предназначен другому приложению");
        }
    }

    private GoogleIdentity toGoogleIdentity(Claims claims) {
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AuthException("В Google ID-token отсутствует sub");
        }
        String email = claims.get("email", String.class);
        // email_verified может прийти булевым или строкой ("true") — приводим устойчиво.
        Object verified = claims.get("email_verified");
        boolean emailVerified = (verified instanceof Boolean b)
                ? b
                : Boolean.parseBoolean(String.valueOf(verified));
        return new GoogleIdentity(sub, email, emailVerified);
    }
}
