package uz.topdim.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uz.topdim.identity.dto.TelegramAuthRequest;
import uz.topdim.identity.exception.AuthException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramLoginVerifierTest {

    private static final String BOT_TOKEN = "123456:TEST-bot-token";

    @Test
    @DisplayName("Валидная подпись + свежий auth_date → проходит")
    void validSignature_passes() {
        TelegramLoginVerifier verifier = new TelegramLoginVerifier(BOT_TOKEN, 300);
        assertThatCode(() -> verifier.verify(signed(Instant.now().getEpochSecond())))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Подделанный hash → AuthException")
    void tamperedHash_throws() {
        TelegramLoginVerifier verifier = new TelegramLoginVerifier(BOT_TOKEN, 300);
        TelegramAuthRequest req = signed(Instant.now().getEpochSecond());
        req.setHash("deadbeefdeadbeef");
        assertThatThrownBy(() -> verifier.verify(req)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("Поле изменено после подписи → подпись не сходится → AuthException")
    void tamperedPayload_throws() {
        TelegramLoginVerifier verifier = new TelegramLoginVerifier(BOT_TOKEN, 300);
        TelegramAuthRequest req = signed(Instant.now().getEpochSecond());
        req.setId(999L); // hash считался для id=555
        assertThatThrownBy(() -> verifier.verify(req)).isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("Устаревший auth_date → AuthException (анти-replay)")
    void staleAuthDate_throws() {
        TelegramLoginVerifier verifier = new TelegramLoginVerifier(BOT_TOKEN, 300);
        assertThatThrownBy(() -> verifier.verify(signed(Instant.now().getEpochSecond() - 3600)))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("auth_date из будущего сверх перекоса → AuthException")
    void futureAuthDate_throws() {
        TelegramLoginVerifier verifier = new TelegramLoginVerifier(BOT_TOKEN, 300);
        assertThatThrownBy(() -> verifier.verify(signed(Instant.now().getEpochSecond() + 3600)))
                .isInstanceOf(AuthException.class);
    }

    @Test
    @DisplayName("Токен бота не задан → вход через Telegram отключён → AuthException")
    void disabled_throws() {
        TelegramLoginVerifier verifier = new TelegramLoginVerifier("", 300);
        assertThatThrownBy(() -> verifier.verify(signed(Instant.now().getEpochSecond())))
                .isInstanceOf(AuthException.class);
    }

    // --- helpers ---

    private TelegramAuthRequest signed(long authDate) {
        TelegramAuthRequest req = new TelegramAuthRequest();
        req.setId(555L);
        req.setFirstName("Иван");
        req.setUsername("ivan");
        req.setAuthDate(authDate);
        req.setHash(computeHash(req, BOT_TOKEN));
        return req;
    }

    /** Повторяет серверный алгоритм для генерации валидного hash в тесте. */
    private static String computeHash(TelegramAuthRequest req, String token) {
        TreeMap<String, String> f = new TreeMap<>();
        f.put("id", String.valueOf(req.getId()));
        f.put("auth_date", String.valueOf(req.getAuthDate()));
        if (req.getFirstName() != null) f.put("first_name", req.getFirstName());
        if (req.getLastName() != null) f.put("last_name", req.getLastName());
        if (req.getUsername() != null) f.put("username", req.getUsername());
        if (req.getPhotoUrl() != null) f.put("photo_url", req.getPhotoUrl());

        StringBuilder sb = new StringBuilder();
        f.forEach((k, v) -> {
            if (sb.length() > 0) sb.append('\n');
            sb.append(k).append('=').append(v);
        });
        try {
            byte[] secret = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(sb.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
