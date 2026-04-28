package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для запроса правок партнёром при финальном согласовании купона.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequestCouponRevisionRequest {

    @NotBlank(message = "Укажите, что нужно исправить")
    @Size(max = 2000, message = "Комментарий не более 2000 символов")
    private String comment;
}
