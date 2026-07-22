package uz.topdim.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO ответа купленного купона.
 * Используется вместо raw entity для безопасной сериализации.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchasedCouponResponse {
    private Long id;
    private Long couponOfferId;
    private Long couponOptionId;
    private String couponTitle;
    private String optionTitle;
    /** Уплаченная цена за этот купон (unit_price на момент покупки). Null для старых заказов без бэкофилла. */
    private BigDecimal pricePaid;
    private String couponCode;
    private String qrToken;
    private String status;
    private Long merchantId;
    private String merchantName;
    private String merchantAddress;
    private String merchantPhone;
    private String merchantWorkingHours;
    private LocalDateTime purchasedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;

    // Refund fields (null when no refund request)
    private Long refundRequestId;
    private String refundStatus;
    private LocalDateTime refundExpectedAt;
}
