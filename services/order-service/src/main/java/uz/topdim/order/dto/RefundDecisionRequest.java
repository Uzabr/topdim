package uz.topdim.order.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RefundDecisionRequest {
    @Size(max = 1000, message = "Комментарий не должен превышать 1000 символов")
    private String adminComment;
}
