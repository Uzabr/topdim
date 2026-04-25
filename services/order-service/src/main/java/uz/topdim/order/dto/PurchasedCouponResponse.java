package uz.topdim.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
}
