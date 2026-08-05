package uz.topdim.identity.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * Реальная отправка OTP через Eskiz.uz SMS API.
 * Активируется когда eskiz.enabled=true (см. {@link LoggingSmsSender} — безусловный фолбэк).
 * <p>
 * Флоу: логин по email/паролю → Bearer-токен кэшируется в памяти → отправка SMS с этим токеном.
 * На 401 при отправке — один перелогин и один повтор отправки. Если и это не помогло —
 * ошибка логируется (log.error), поток вызывающего кода (OtpService) не падает.
 */
@Slf4j
@Component
@Primary
@ConditionalOnProperty(name = "eskiz.enabled", havingValue = "true")
public class EskizSmsSender implements SmsSender {

    private final RestClient restClient;
    private final String email;
    private final String password;
    private final String from;
    private final String template;

    private volatile String token;

    // @Autowired обязателен: у класса ДВА конструктора (второй — package-private для тестов),
    // и без явной пометки Spring не может выбрать конструктор → падение контекста при старте.
    @Autowired
    public EskizSmsSender(
            RestClient.Builder restClientBuilder,
            @Value("${eskiz.base-url:https://notify.eskiz.uz/api}") String baseUrl,
            @Value("${eskiz.email:}") String email,
            @Value("${eskiz.password:}") String password,
            @Value("${eskiz.from:4546}") String from,
            @Value("${eskiz.template:SizBiz tasdiqlash kodi: {code}}") String template,
            @Value("${eskiz.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${eskiz.read-timeout-ms:10000}") long readTimeoutMs
    ) {
        this(buildRestClient(restClientBuilder, baseUrl, connectTimeoutMs, readTimeoutMs), email, password, from, template);
        log.info("EskizSmsSender activated — реальные SMS будут отправляться через {} (connect={}ms, read={}ms)",
                baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    /**
     * Package-private: для юнит-тестов с уже собранным {@link RestClient} (например, через
     * {@code MockRestServiceServer.bindTo(RestClient.Builder)}). В отличие от публичного конструктора,
     * НЕ трогает {@code requestFactory} — иначе таймаут-обвязка продакшен-конструктора затёрла бы
     * мок, подставленный в {@code RestClient.Builder} до вызова этого конструктора.
     */
    EskizSmsSender(RestClient restClient, String email, String password, String from, String template) {
        this.restClient = restClient;
        this.email = email;
        this.password = password;
        this.from = from;
        this.template = template;
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.warn("eskiz.enabled=true, но ESKIZ_EMAIL/ESKIZ_PASSWORD не заданы — SMS не будут доставляться");
        }
    }

    /** Явные connect/read timeout — без них синхронный вызов к Eskiz может зависнуть без ограничения по времени. */
    private static RestClient buildRestClient(RestClient.Builder builder, String baseUrl,
                                               long connectTimeoutMs, long readTimeoutMs) {
        ClientHttpRequestFactory requestFactory = ClientHttpRequestFactoryBuilder.detect()
                .build(ClientHttpRequestFactorySettings.defaults()
                        .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                        .withReadTimeout(Duration.ofMillis(readTimeoutMs)));
        return builder.baseUrl(baseUrl).requestFactory(requestFactory).build();
    }

    @Override
    public void sendOtp(String phone, String code) {
        String message = template.replace("{code}", code);
        String normalizedPhone = normalizePhone(phone);

        String currentToken = token;
        if (currentToken == null) {
            currentToken = login();
            if (currentToken == null) {
                return; // login() уже залогировал причину
            }
        }

        SendResult result = trySend(normalizedPhone, message, currentToken);

        if (result.outcome() == SendOutcome.UNAUTHORIZED) {
            // Токен из кэша протух (401) — он не годится, форсируем реальный перелогин:
            // без этого double-check в login() тут же вернул бы этот же протухший токен.
            token = null;
            String freshToken = login();
            if (freshToken == null) {
                return; // login() уже залогировал причину
            }
            result = trySend(normalizedPhone, message, freshToken);
        }

        if (result.outcome() != SendOutcome.SUCCESS) {
            log.error("Eskiz: не удалось отправить OTP-SMS на {} — {}", maskPhone(phone), result.detail(), result.cause());
        }
    }

    private synchronized String login() {
        if (token != null) {
            // Double-check: пока этот поток ждал лок, другой поток уже успешно залогинился —
            // нет смысла бить Eskiz повторным логином (актуально для холодного старта:
            // несколько первых sendOtp() параллельно приходят с token == null).
            return token;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("email", email);
        form.add("password", password);

        try {
            EskizLoginResponse response = restClient.post()
                    .uri("/auth/login")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(EskizLoginResponse.class);

            String newToken = (response != null && response.data() != null) ? response.data().token() : null;
            if (!StringUtils.hasText(newToken)) {
                log.error("Eskiz: логин выполнен, но токен не получен в ответе");
                return null;
            }
            this.token = newToken;
            return newToken;
        } catch (RestClientException e) {
            log.error("Eskiz: не удалось авторизоваться — {}", e.getMessage(), e);
            return null;
        }
    }

    private SendResult trySend(String phone, String message, String bearerToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mobile_phone", phone);
        form.add("message", message);
        form.add("from", from);

        try {
            restClient.post()
                    .uri("/message/sms/send")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
            return SendResult.success();
        } catch (RestClientResponseException e) {
            String detail = "HTTP %d %s: %s".formatted(e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
            SendOutcome outcome = (e.getStatusCode().value() == 401) ? SendOutcome.UNAUTHORIZED : SendOutcome.ERROR;
            return new SendResult(outcome, detail, e);
        } catch (RestClientException e) {
            return new SendResult(SendOutcome.ERROR, e.getMessage(), e);
        }
    }

    /** Eskiz ожидает номер без "+", формат 998XXXXXXXXX. */
    private String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[^0-9]", "");
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return phone.substring(0, 2) + "***" + phone.substring(phone.length() - 2);
    }

    private enum SendOutcome {
        SUCCESS, UNAUTHORIZED, ERROR
    }

    /** {@code detail}/{@code cause} заполнены только когда {@code outcome != SUCCESS} — нужны для error-лога. */
    private record SendResult(SendOutcome outcome, String detail, Throwable cause) {
        static SendResult success() {
            return new SendResult(SendOutcome.SUCCESS, null, null);
        }
    }

    private record EskizLoginResponse(EskizLoginData data) {
    }

    private record EskizLoginData(String token) {
    }
}
