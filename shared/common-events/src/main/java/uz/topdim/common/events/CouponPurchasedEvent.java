package uz.topdim.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponPurchasedEvent implements Serializable {
    private Long purchasedCouponId;
    private Long userId;
    private Long orderId;
    private String couponCode;
    private String userEmail;
    private String userPhone;
    private String couponTitle;
    private LocalDateTime purchasedAt;
}
