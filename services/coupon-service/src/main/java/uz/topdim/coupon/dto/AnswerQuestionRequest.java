package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AnswerQuestionRequest {

    @NotBlank(message = "Ответ обязателен")
    @Size(min = 1, max = 2000, message = "Ответ должен быть от 1 до 2000 символов")
    private String answer;
}
