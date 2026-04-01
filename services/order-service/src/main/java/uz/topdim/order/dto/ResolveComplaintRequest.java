package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResolveComplaintRequest {
    
    @NotBlank(message = "Решение обязательно (RESOLVE / REJECT)")
    private String decision;
    
    @NotBlank(message = "Текст решения/ответа обязателен")
    private String resolution;
}
