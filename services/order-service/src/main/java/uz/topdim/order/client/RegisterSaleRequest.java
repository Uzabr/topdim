package uz.topdim.order.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Запрос на регистрацию продажи купона в coupon-service.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSaleRequest {
    private Long orderId;
    private int quantity;
    private BigDecimal amount;
}
