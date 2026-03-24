package uz.topdim.common.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Событие покупки купона (RabbitMQ).
 * Публикуется order-service → слушает notification-service.
 * Содержит: email, phone, couponTitle, couponCode.
 */
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
