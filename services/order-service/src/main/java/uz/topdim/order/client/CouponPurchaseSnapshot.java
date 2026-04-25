package uz.topdim.order.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponPurchaseSnapshot {
    private Long couponOfferId;
    private Long couponOptionId;
    private String couponTitle;
    private String optionTitle;
    private String couponStatus;
    private String optionStatus;
    private BigDecimal couponPrice;
    private Integer quantityLimit;
    private int quantitySold;
    private Long merchantId;
    private String merchantName;
    private String merchantAddress;
    private String merchantPhone;
    private String merchantWorkingHours;
    private LocalDateTime buyUntil;
    private LocalDateTime useUntil;
}
