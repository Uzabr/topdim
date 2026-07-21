package uz.topdim.coupon.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RejectQuestionRequest {

    @Size(max = 1000, message = "Причина отклонения должна быть до 1000 символов")
    private String rejectReason;
}
