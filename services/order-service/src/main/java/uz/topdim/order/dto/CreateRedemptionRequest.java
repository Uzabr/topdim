package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateRedemptionRequest {
    @NotBlank(message = "Код купона обязателен")
    private String couponCode;

    private String staffName;
}
