package uz.topdim.identity.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uz.topdim.identity.dto.TelegramAuthRequest;
import uz.topdim.identity.exception.AuthException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;

/**
 * Проверяет подпись Telegram Login Widget и свежесть данных.
 *
 * <p>Алгоритм (core.telegram.org/widgets/login#checking-authorization):
 * <pre>
 *   secret_key        = SHA256(bot_token)                              // raw bytes
 *   data_check_string = join("\n", "key=value" по всем полям кроме hash, ключи отсортированы)
 *   expected_hash     = hex( HMAC_SHA256(data_check_string, secret_key) )
 * </pre>
 * Сравнение hash — постоянного времени; дополнительно проверяется свежесть
 * {@code auth_date} (анти-replay). Токен бота читается из {@code telegram.bot-token}
 * (env {@code TELEGRAM_BOT_TOKEN}); если не задан — вход через Telegram отключён.
 */
@Slf4j
@Component
public class TelegramLoginVerifier {

    private final byte[] secretKey;
    private final boolean enabled;
    private final long maxAgeSeconds;

    public TelegramLoginVerifier(
            @Value("${telegram.bot-token:}") String botToken,
            @Value("${telegram.auth-max-age-seconds:300}") long maxAgeSeconds) {
        this.enabled = botToken != null && !botToken.isBlank();
        this.secretKey = enabled ? sha256(botToken.getBytes(StandardCharsets.UTF_8)) : null;
        this.maxAgeSeconds = maxAgeSeconds;
        if (!enabled) {
            log.warn("Telegram login disabled: telegram.bot-token не задан");
        }
    }

    /**
     * Проверяет payload. Бросает {@link AuthException} при любой невалидности
     * (не настроено, устаревшие данные, неверная подпись).
     */
    public void verify(TelegramAuthRequest req) {
        if (!enabled) {
            throw new AuthException("Telegram-авторизация не настроена");
        }
        verifyFreshness(req.getAuthDate());
        verifyHash(req);
    }

    private void verifyFreshness(Long authDate) {
        if (authDate == null) {
            throw new AuthException("Отсутствует auth_date");
        }
        long age = Instant.now().getEpochSecond() - authDate;
        // Устарело (replay) либо из будущего сверх допустимого перекоса часов.
        if (age > maxAgeSeconds || age < -60) {
            throw new AuthException("Данные Telegram устарели, повторите вход");
        }
    }

    private void verifyHash(TelegramAuthRequest req) {
        String expected = hmacSha256Hex(
                buildDataCheckString(req).getBytes(StandardCharsets.UTF_8), secretKey);
        String provided = req.getHash() == null ? "" : req.getHash().trim().toLowerCase();
        if (!MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8))) {
            throw new AuthException("Невалидная подпись Telegram");
        }
    }

    /** Все переданные поля (кроме hash) как {@code key=value}, ключи по алфавиту, через {@code \n}. */
    private String buildDataCheckString(TelegramAuthRequest req) {
        TreeMap<String, String> fields = new TreeMap<>();
        fields.put("id", String.valueOf(req.getId()));
        fields.put("auth_date", String.valueOf(req.getAuthDate()));
        if (req.getFirstName() != null) fields.put("first_name", req.getFirstName());
        if (req.getLastName() != null)  fields.put("last_name", req.getLastName());
        if (req.getUsername() != null)  fields.put("username", req.getUsername());
        if (req.getPhotoUrl() != null)  fields.put("photo_url", req.getPhotoUrl());

        StringBuilder sb = new StringBuilder();
        fields.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 недоступен", e);
        }
    }

    private static String hmacSha256Hex(byte[] data, byte[] key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 недоступен", e);
        }
    }
}
