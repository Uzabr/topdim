package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateCouponRefundRequest {
    @NotNull(message = "ID купленного купона обязателен")
    private Long purchasedCouponId;

    @NotBlank(message = "Причина возврата обязательна")
    @Size(min = 10, max = 1000, message = "Причина должна быть от 10 до 1000 символов")
    private String reason;
}
