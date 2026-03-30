package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StatusUpdateRequest {
    
    @NotBlank(message = "Статус обязателен")
    private String status;
    
    private String reason;
}
