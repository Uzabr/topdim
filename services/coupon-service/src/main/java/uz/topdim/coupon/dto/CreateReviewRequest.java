package uz.topdim.coupon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateReviewRequest {
    @NotNull(message = "ID купона обязателен")
    private Long couponOfferId;

    @Min(value = 1, message = "Рейтинг от 1 до 5")
    @Max(value = 5, message = "Рейтинг от 1 до 5")
    private int rating;

    @NotBlank(message = "Комментарий не может быть пустым")
    private String comment;
}
