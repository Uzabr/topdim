package uz.topdim.identity.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Тянет и кэширует набор публичных RSA-ключей Google (JWKS) для проверки подписи ID-token.
 *
 * <p>Источник — {@code https://www.googleapis.com/oauth2/v3/certs}: JSON вида
 * {@code { "keys": [ { "kid","kty":"RSA","use":"sig","alg":"RS256","n","e" }, ... ] }}.
 * Из {@code n}/{@code e} (base64url) собирается {@link java.security.interfaces.RSAPublicKey}.
 *
 * <p><b>Кэш и ротация.</b> Google периодически ротирует ключи и отдаёт срок жизни кэша в
 * заголовке {@code Cache-Control: max-age}. Ключи кэшируются до истечения этого срока, после
 * чего перезапрашиваются. Дополнительно, если запрошенный {@code kid} не найден в кэше (сильный
 * сигнал ротации), выполняется принудительное обновление — но не чаще, чем раз в
 * {@value #MIN_REFRESH_INTERVAL_SECONDS} с, чтобы поток мусорных {@code kid} не превратился
 * в усилитель исходящих запросов к Google.
 *
 * <p>Сетевой вызов ленивый — только при первом {@link #resolve(String)}; конструктор I/O не делает,
 * поэтому старт приложения и контекст-тесты от доступности Google не зависят.
 */
@Slf4j
@Component
public class GooglePublicKeyProviderImpl implements GooglePublicKeyProvider {

    private static final Pattern MAX_AGE = Pattern.compile("max-age\\s*=\\s*(\\d+)");
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final long DEFAULT_MAX_AGE_SECONDS = 3600;
    private static final long MIN_MAX_AGE_SECONDS = 60;
    private static final long MIN_REFRESH_INTERVAL_SECONDS = 60;

    private final URI jwksUri;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final Object refreshLock = new Object();
    private volatile Map<String, PublicKey> keysByKid = Map.of();
    private volatile Instant expiresAt = Instant.EPOCH;
    private volatile Instant lastFetchAttempt = Instant.EPOCH;

    public GooglePublicKeyProviderImpl(
            @Value("${google.jwks-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwksUri,
            ObjectMapper objectMapper) {
        this.jwksUri = URI.create(jwksUri);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    @Override
    public PublicKey resolve(String kid) {
        if (kid == null || kid.isBlank()) {
            return null;
        }
        PublicKey key = refreshIfExpired().get(kid);
        if (key != null) {
            return key;
        }
        // kid не найден — возможно, ключи ротировались раньше истечения max-age: пробуем обновить.
        return forceRefreshIfAllowed().get(kid);
    }

    /** Возвращает кэш; если срок истёк — перезапрашивает под локом (double-check). */
    private Map<String, PublicKey> refreshIfExpired() {
        if (Instant.now().isBefore(expiresAt) && !keysByKid.isEmpty()) {
            return keysByKid;
        }
        synchronized (refreshLock) {
            if (Instant.now().isBefore(expiresAt) && !keysByKid.isEmpty()) {
                return keysByKid;
            }
            return fetch();
        }
    }

    /** Принудительное обновление при промахе kid, но не чаще MIN_REFRESH_INTERVAL_SECONDS (анти-амплификация). */
    private Map<String, PublicKey> forceRefreshIfAllowed() {
        synchronized (refreshLock) {
            if (Instant.now().isBefore(lastFetchAttempt.plusSeconds(MIN_REFRESH_INTERVAL_SECONDS))) {
                return keysByKid;
            }
            return fetch();
        }
    }

    /** Выполняет HTTP-запрос JWKS. При любой ошибке сохраняет прежний кэш (fail-safe, а не fail-open). */
    private Map<String, PublicKey> fetch() {
        lastFetchAttempt = Instant.now();
        try {
            HttpRequest request = HttpRequest.newBuilder(jwksUri)
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("JWKS Google вернул HTTP {}", response.statusCode());
                return keysByKid;
            }
            Map<String, PublicKey> parsed = parseJwks(response.body());
            if (parsed.isEmpty()) {
                log.warn("JWKS Google не содержит пригодных RSA-ключей для подписи");
                return keysByKid;
            }
            long maxAge = maxAge(response).orElse(DEFAULT_MAX_AGE_SECONDS);
            keysByKid = parsed;
            expiresAt = Instant.now().plusSeconds(Math.max(maxAge, MIN_MAX_AGE_SECONDS));
            log.debug("JWKS Google обновлён: {} ключ(ей), max-age={}с", parsed.size(), maxAge);
            return parsed;
        } catch (IOException e) {
            log.error("Ошибка запроса JWKS Google: {}", e.getMessage());
            return keysByKid;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Запрос JWKS Google прерван");
            return keysByKid;
        }
    }

    private Map<String, PublicKey> parseJwks(String body) throws IOException {
        JsonNode keys = objectMapper.readTree(body).path("keys");
        Map<String, PublicKey> result = new HashMap<>();
        KeyFactory rsa;
        try {
            rsa = KeyFactory.getInstance("RSA");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA KeyFactory недоступен", e);
        }
        for (JsonNode k : keys) {
            if (!"RSA".equals(k.path("kty").asText())) {
                continue;
            }
            // Берём только ключи для подписи (use=sig); поле опционально — при отсутствии считаем sig.
            if (!"sig".equals(k.path("use").asText("sig"))) {
                continue;
            }
            String kid = k.path("kid").asText(null);
            if (kid == null || kid.isBlank()) {
                continue;
            }
            try {
                BigInteger modulus = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("n").asText()));
                BigInteger exponent = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("e").asText()));
                result.put(kid, rsa.generatePublic(new RSAPublicKeySpec(modulus, exponent)));
            } catch (GeneralSecurityException | IllegalArgumentException e) {
                log.warn("Пропущен некорректный JWKS-ключ kid={}: {}", kid, e.getMessage());
            }
        }
        return Map.copyOf(result);
    }

    private OptionalLong maxAge(HttpResponse<?> response) {
        return response.headers().firstValue("Cache-Control")
                .map(MAX_AGE::matcher)
                .filter(Matcher::find)
                .map(m -> {
                    try {
                        return OptionalLong.of(Long.parseLong(m.group(1)));
                    } catch (NumberFormatException e) {
                        return OptionalLong.empty();
                    }
                })
                .orElseGet(OptionalLong::empty);
    }
}
