package uz.topdim.payment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.topdim.common.dto.ApiResponse;
import uz.topdim.payment.entity.Payment;
import uz.topdim.payment.service.PaymentService;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<ApiResponse<Payment>> createPayment(@RequestBody Map<String, Object> request) {
        Long orderId = Long.valueOf(request.get("orderId").toString());
        Long userId = Long.valueOf(request.getOrDefault("userId", "0").toString());
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String provider = request.getOrDefault("provider", "payme").toString();

        Payment payment = paymentService.createPayment(orderId, userId, amount, provider);
        return ResponseEntity.ok(ApiResponse.success("Платёж создан", payment));
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<ApiResponse<Payment>> getPaymentStatus(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPayment(id)));
    }

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<Payment>> getPaymentByOrder(@PathVariable Long orderId) {
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByOrderId(orderId)));
    }

    @PostMapping("/callback")
    public ResponseEntity<ApiResponse<Payment>> handleCallback(@RequestBody Map<String, Object> callback) {
        Long orderId = Long.valueOf(callback.get("orderId").toString());
        Long userId = Long.valueOf(callback.getOrDefault("userId", "0").toString());
        BigDecimal amount = new BigDecimal(callback.get("amount").toString());
        String provider = callback.getOrDefault("provider", "payme").toString();
        String transactionId = callback.getOrDefault("transactionId", "").toString();

        Payment payment = paymentService.handleCallback(orderId, userId, amount, provider, transactionId);
        return ResponseEntity.ok(ApiResponse.success("Платёж обработан", payment));
    }
}
