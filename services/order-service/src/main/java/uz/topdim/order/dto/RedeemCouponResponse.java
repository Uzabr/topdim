package uz.topdim.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedeemCouponResponse {
    private Long purchasedCouponId;
    private Long couponOfferId;
    private Long couponOptionId;
    private String couponTitle;
    private String optionTitle;
    private String couponCode;
    private String status;
    private Long merchantId;
    private String merchantName;
    private LocalDateTime purchasedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime usedAt;
}
