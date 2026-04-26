package uz.topdim.coupon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Запрос на регистрацию продажи купона.
 * Вызывается order-service после оплаты заказа.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterCouponSaleRequest {

    @NotNull
    private Long orderId;

    @Min(1)
    private int quantity;

    @NotNull
    private BigDecimal amount;
}
