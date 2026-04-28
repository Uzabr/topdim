package uz.topdim.order.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Admin-only lookup response for purchased coupons.
 * Does NOT expose qrToken for security reasons.
 */
@Data
@Builder
public class AdminPurchasedCouponLookupResponse {
    private Long purchasedCouponId;
    private Long orderId;
    private Long userId;
    private Long couponOfferId;
    private Long couponOptionId;
    private String couponTitle;
    private String optionTitle;
    private String couponCode;
    private String status;
    private Long merchantId;
    private String merchantName;
    private String merchantAddress;
    private LocalDateTime purchasedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
