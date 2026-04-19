package uz.topdim.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Storefront-safe DTO ответа заказа.
 * Гарантированно содержит orderId, status, totalAmount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private BigDecimal totalAmount;
    private String status;
    private String userEmail;
    private String userPhone;
    private int itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
