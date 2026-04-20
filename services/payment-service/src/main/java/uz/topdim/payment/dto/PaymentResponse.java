package uz.topdim.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO ответа платежа.
 * Поля: id, orderId, amount, status, paymentUrl.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private Long id;
    private Long orderId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private String provider;
    private String statusName;
    private String transactionId;
    private String paymentUrl;
    /** Режим оплаты: "demo" или "provider". Определяет UX на фронтенде. */
    private String paymentMode;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
