package uz.topdim.coupon.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.coupon.dto.CouponOfferResponse;
import uz.topdim.coupon.dto.RevisionRequest;
import uz.topdim.coupon.service.CouponOfferService;

/**
 * Webhook-контроллер для Telegram-бота.
 * Принимает решения мерчанта (одобрить / запросить правки).
 *
 * <p>Эндпоинты вызываются внутренним Telegram-сервисом,
 * поэтому не требуют проверки роли через @PreAuthorize.
 * Доступ ограничен по API-ключу через заголовок X-Bot-Api-Key.
 */
@RestController
@RequestMapping("/api/v1/bot/coupons")
@RequiredArgsConstructor
public class BotWebhookController {

    private final CouponOfferService couponOfferService;

    @Value("${app.bot.api-key:}")
    private String botApiKey;

    /**
     * Проверяет API-ключ бота.
     * @throws ResponseStatusException 401 если ключ невалидный
     */
    private void validateBotApiKey(String apiKey) {
        if (botApiKey.isEmpty()) {
            // Ключ не настроен — пропускаем (dev-режим)
            return;
        }
        if (apiKey == null || !botApiKey.equals(apiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid bot API key");
        }
    }

    /**
     * Мерчант одобряет купон → ACTIVE.
     * Вызывается Telegram-ботом при нажатии кнопки "Одобрить".
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> approve(
            @PathVariable Long id,
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKey) {
        validateBotApiKey(apiKey);
        return ResponseEntity.ok(ApiResponse.success(
                "Купон одобрен мерчантом", couponOfferService.approveByMerchant(id)));
    }

    /**
     * Мерчант запрашивает правки → REVISION_REQUESTED.
     * Вызывается Telegram-ботом при нажатии кнопки "Запросить правки".
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<CouponOfferResponse>> reject(
            @PathVariable Long id,
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKey,
            @Valid @RequestBody RevisionRequest request) {
        validateBotApiKey(apiKey);
        return ResponseEntity.ok(ApiResponse.success(
                "Купон возвращён на доработку", couponOfferService.requestRevisionByMerchant(id, request.getComment())));
    }

    /**
     * Создает новый купон (LEAD) на основе заявки из Telegram-бота.
     */
    @PostMapping("/leads")
    public ResponseEntity<ApiResponse<Void>> createLead(
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKey,
            @Valid @RequestBody uz.topdim.coupon.dto.BotLeadRequest request) {
        validateBotApiKey(apiKey);
        couponOfferService.createLeadFromBot(request);
        return ResponseEntity.ok(ApiResponse.success("Заявка успешно принята", null));
    }

    /**
     * Получить все купоны мерчанта по его telegramChatId.
     */
    @GetMapping("/merchants/{chatId}")
    public ResponseEntity<ApiResponse<java.util.List<CouponOfferResponse>>> getMyCoupons(
            @PathVariable String chatId,
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKey) {
        validateBotApiKey(apiKey);
        return ResponseEntity.ok(ApiResponse.success(
                "Купоны партнёра", couponOfferService.getMyCouponsByTelegramId(chatId)));
    }

    /**
     * Получить статистику купона.
     */
    @GetMapping("/{id}/stats")
    public ResponseEntity<ApiResponse<uz.topdim.coupon.dto.CouponStatsResponse>> getStats(
            @PathVariable Long id,
            @RequestHeader(value = "X-Bot-Api-Key", required = false) String apiKey) {
        validateBotApiKey(apiKey);
        return ResponseEntity.ok(ApiResponse.success(
                "Статистика купона", couponOfferService.getCouponStats(id)));
    }
}
