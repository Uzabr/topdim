package uz.topdim.identity.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

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

    public EskizSmsSender(
            RestClient.Builder restClientBuilder,
            @Value("${eskiz.base-url:https://notify.eskiz.uz/api}") String baseUrl,
            @Value("${eskiz.email:}") String email,
            @Value("${eskiz.password:}") String password,
            @Value("${eskiz.from:4546}") String from,
            @Value("${eskiz.template:SizBiz tasdiqlash kodi: {code}}") String template
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.email = email;
        this.password = password;
        this.from = from;
        this.template = template;
        log.info("EskizSmsSender activated — реальные SMS будут отправляться через {}", baseUrl);
        if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
            log.warn("eskiz.enabled=true, но ESKIZ_EMAIL/ESKIZ_PASSWORD не заданы — SMS не будут доставляться");
        }
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

        SendOutcome outcome = trySend(normalizedPhone, message, currentToken);

        if (outcome == SendOutcome.UNAUTHORIZED) {
            String freshToken = login();
            if (freshToken == null) {
                return; // login() уже залогировал причину
            }
            outcome = trySend(normalizedPhone, message, freshToken);
        }

        if (outcome != SendOutcome.SUCCESS) {
            log.error("Eskiz: не удалось отправить OTP-SMS на {}", maskPhone(phone));
        }
    }

    private synchronized String login() {
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
            log.error("Eskiz: не удалось авторизоваться — {}", e.getMessage());
            return null;
        }
    }

    private SendOutcome trySend(String phone, String message, String bearerToken) {
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
            return SendOutcome.SUCCESS;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                return SendOutcome.UNAUTHORIZED;
            }
            return SendOutcome.ERROR;
        } catch (RestClientException e) {
            return SendOutcome.ERROR;
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

    private record EskizLoginResponse(EskizLoginData data) {
    }

    private record EskizLoginData(String token) {
    }
}
