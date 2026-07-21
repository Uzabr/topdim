package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateQuestionRequest {

    @NotNull(message = "ID купона обязателен")
    private Long couponOfferId;

    @NotBlank(message = "Вопрос обязателен")
    @Size(min = 5, max = 1000, message = "Вопрос должен быть от 5 до 1000 символов")
    private String question;
}
