package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для отклонения заявки партнёра на акцию.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectCouponRequest {

    @NotBlank(message = "Укажите причину отклонения")
    @Size(max = 2000, message = "Причина не более 2000 символов")
    private String reason;
}
