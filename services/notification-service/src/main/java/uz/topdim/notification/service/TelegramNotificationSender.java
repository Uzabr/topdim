package uz.topdim.notification.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;

@Service
public class TelegramNotificationSender {

    private static final String TELEGRAM_API = "https://api.telegram.org";

    private final RestClient restClient;
    private final String botToken;
    private final String partnerBaseUrl;

    @Autowired
    public TelegramNotificationSender(
            RestClient.Builder restClientBuilder,
            @Value("${notification.telegram.bot-token:${TELEGRAM_BOT_TOKEN:}}") String botToken,
            @Value("${notification.telegram.partner-base-url:https://partner.sizbiz.uz}")
            String partnerBaseUrl,
            @Value("${notification.telegram.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${notification.telegram.read-timeout-ms:10000}") int readTimeoutMs
    ) {
        this(
                restClientBuilder
                        .baseUrl(TELEGRAM_API)
                        .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(
                                ClientHttpRequestFactorySettings.defaults()
                                        .withConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                                        .withReadTimeout(Duration.ofMillis(readTimeoutMs))))
                        .build(),
                botToken,
                partnerBaseUrl
        );
    }

    TelegramNotificationSender(
            RestClient restClient,
            String botToken,
            String partnerBaseUrl
    ) {
        this.restClient = restClient;
        this.botToken = botToken;
        this.partnerBaseUrl = stripTrailingSlash(partnerBaseUrl);
    }

    public void send(Long chatId, String message, String deepLink) {
        if (botToken == null || botToken.isBlank()) {
            throw new TelegramDeliveryException("Telegram bot не настроен");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("chat_id", chatId.toString());
        form.add("text", buildText(message, deepLink));

        try {
            restClient.post()
                    .uri(TELEGRAM_API + "/bot" + botToken + "/sendMessage")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new TelegramDeliveryException("Telegram delivery failed", exception);
        }
    }

    private String buildText(String message, String deepLink) {
        if (deepLink == null || deepLink.isBlank()) {
            return message;
        }
        String absoluteLink = deepLink.startsWith("http://") || deepLink.startsWith("https://")
                ? deepLink
                : partnerBaseUrl + (deepLink.startsWith("/") ? deepLink : "/" + deepLink);
        return message + "\n" + absoluteLink;
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://partner.sizbiz.uz";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
