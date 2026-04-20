package uz.topdim.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.payment.dto.PaymentResponse;
import uz.topdim.payment.entity.Payment;
import uz.topdim.payment.mapper.PaymentMapper;
import uz.topdim.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * REST контроллер платежей.
 * Endpoints: создание, статус, поиск по orderId, webhook от провайдера, demo completion.
 * Все storefront-facing endpoints возвращают PaymentResponse DTO.
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;

    /** Режим оплаты: "demo" (без реальной ПС) или "provider" (реальная ПС). */
    @Value("${payment.mode:demo}")
    private String paymentMode;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<PaymentResponse>> createPayment(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        Long userId = Long.valueOf(request.getOrDefault("userId", "0").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String provider = request.getOrDefault("provider", "payme").toString();

        Payment payment = paymentService.createPayment(orderId, userId, amount, provider);
        return ResponseEntity.ok(ApiResponse.success("Платёж создан", toResponseWithMode(payment)));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentStatus(@PathVariable Long id) {
        Payment payment = paymentService.getPayment(id);
        return ResponseEntity.ok(ApiResponse.success(toResponseWithMode(payment)));
    }

    /**
     * Получить платёж по orderId.
     * Ключевой endpoint для storefront polling после создания order.
     * Возвращает 404 если payment ещё не создан (event-driven задержка).
     */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<PaymentResponse>> getPaymentByOrder(@PathVariable Long orderId) {
        Optional<Payment> payment = paymentService.findPaymentByOrderId(orderId);
        if (payment.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Платёж для заказа ещё не создан"));
        }
        return ResponseEntity.ok(ApiResponse.success(toResponseWithMode(payment.get())));
    }

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<PaymentResponse>> handleCallback(@RequestBody Map<String, Object> callback) {
        Long orderId = Long.valueOf(callback.get("orderId").toString());
        Long userId = Long.valueOf(callback.getOrDefault("userId", "0").toString());
        BigDecimal amount = new BigDecimal(callback.get("amount").toString());
        String provider = callback.getOrDefault("provider", "payme").toString();
        String transactionId = callback.getOrDefault("transactionId", "").toString();

        Payment payment = paymentService.handleCallback(orderId, userId, amount, provider, transactionId);
        return ResponseEntity.ok(ApiResponse.success("Платёж обработан", toResponseWithMode(payment)));
    }

    /**
     * Demo completion — завершает платёж без реального провайдера.
     * Доступен ТОЛЬКО в payment.mode=demo.
     * Идемпотентен: повторные вызовы безопасны.
     *
     * @param orderId ID заказа
     * @return обновлённый PaymentResponse
     */
    @PostMapping("/order/{orderId}/demo-complete")
    public ResponseEntity<ApiResponse<PaymentResponse>> demoComplete(@PathVariable Long orderId) {
        if (!"demo".equalsIgnoreCase(paymentMode)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("Demo completion недоступен в production mode"));
        }

        Payment payment = paymentService.demoComplete(orderId);
        return ResponseEntity.ok(ApiResponse.success("Демо-оплата подтверждена", toResponseWithMode(payment)));
    }

    /**
     * Обогащает PaymentResponse полем paymentMode из конфигурации,
     * чтобы фронтенд знал — показывать demo CTA или redirect к реальной ПС.
     */
    private PaymentResponse toResponseWithMode(Payment payment) {
        PaymentResponse response = paymentMapper.toResponse(payment);
        response.setPaymentMode(paymentMode);
        return response;
    }
}
