package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ArchiveCouponRequest {

    @NotBlank(message = "Причина архивирования обязательна")
    @Size(max = 1000, message = "Причина архивирования не должна превышать 1000 символов")
    private String reason;
}
