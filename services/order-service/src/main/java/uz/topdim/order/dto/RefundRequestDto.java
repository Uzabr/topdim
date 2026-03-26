package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO запроса на возврат средств.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefundRequestDto {

    @NotBlank(message = "Причина возврата обязательна")
    @Size(min = 10, max = 1000, message = "Причина должна быть от 10 до 1000 символов")
    private String reason;
}
