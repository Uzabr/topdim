package uz.topdim.order.dto;

import lombok.Builder;
import lombok.Data;
import uz.topdim.order.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Minimal order projection for admin support screens.
 * Deliberately excludes order items, purchased coupons, and QR redemption tokens.
 */
@Data
@Builder
public class AdminOrderResponse {
    private Long id;
    private String orderNumber;
    private Long userId;
    private String userEmail;
    private String userPhone;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
}
