package uz.topdim.coupon.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import uz.topdim.coupon.entity.CouponOffer;

import java.util.HashMap;
import java.util.Map;

/**
 * Сервис отправки превью купона в Telegram-бот.
 * Вызывает POST /webhook/preview на стороне бота.
 */
@Service
@Slf4j
public class TelegramPreviewService {

    private final RestTemplate restTemplate;

    @Value("${app.bot.preview-url:http://localhost:3002/webhook/preview}")
    private String previewWebhookUrl;

    @Value("${app.bot.preview-token:}")
    private String previewWebhookToken;

    public TelegramPreviewService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Отправить превью купона мерчанту через Telegram-бот.
     *
     * @param offer купон для отправки
     */
    public void sendPreview(CouponOffer offer) {
        String chatId = offer.getMerchant() != null ? offer.getMerchant().getTelegramChatId() : null;

        if (chatId == null || chatId.isBlank()) {
            log.warn("Купон #{}: у мерчанта '{}' не настроен telegram_chat_id, превью не отправлено",
                    offer.getId(), offer.getMerchant() != null ? offer.getMerchant().getName() : "unknown");
            return;
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("chatId", chatId);
        payload.put("couponId", offer.getId());
        payload.put("title", offer.getTitle());
        payload.put("oldPrice", offer.getOldPrice());
        payload.put("newPrice", offer.getFromPrice());
        payload.put("coverImageUrl", offer.getCoverImageUrl());
        payload.put("description", CouponOfferService.derivePreview(offer.getOfferDescription(), 150));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (previewWebhookToken != null && !previewWebhookToken.isBlank()) {
            headers.set("X-Webhook-Token", previewWebhookToken);
        }

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    previewWebhookUrl, HttpMethod.POST, entity, String.class);

            log.info("Превью купона #{} отправлено в Telegram (chatId={}), ответ: {}",
                    offer.getId(), chatId, response.getStatusCode());
        } catch (Exception e) {
            log.error("Не удалось отправить превью купона #{} в Telegram: {}",
                    offer.getId(), e.getMessage());
            // Не бросаем исключение — статус купона уже изменён, превью — best-effort
        }
    }

    /**
     * Отправить обычное push-сообщение в Telegram-бот.
     *
     * @param chatId Telegram chat ID
     * @param text текст сообщения
     */
    public void sendPushMessage(String chatId, String text) {
        if (chatId == null || chatId.isBlank()) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("chatId", chatId);
        payload.put("text", text);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (previewWebhookToken != null && !previewWebhookToken.isBlank()) {
            headers.set("X-Webhook-Token", previewWebhookToken);
        }

        try {
            // Отправляем на новый эндпоинт бота для простых сообщений
            String pushUrl = previewWebhookUrl.replace("/webhook/preview", "/webhook/push");
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            restTemplate.exchange(pushUrl, HttpMethod.POST, entity, String.class);
        } catch (Exception e) {
            log.error("Не удалось отправить сообщение в Telegram (chatId={}): {}", chatId, e.getMessage());
        }
    }
}
