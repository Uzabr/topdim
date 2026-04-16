package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO запроса на возврат купона на доработку (от партнёра).
 */
@Data
public class RevisionRequest {

    @NotBlank(message = "Комментарий обязателен")
    private String comment;
}
