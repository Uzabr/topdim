package uz.topdim.coupon.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateReviewRequest {
    @NotNull(message = "ID купона обязателен")
    private Long couponOfferId;

    @Min(value = 1, message = "Рейтинг от 1 до 5")
    @Max(value = 5, message = "Рейтинг от 1 до 5")
    private int rating;

    @Size(min = 10, max = 2000, message = "Комментарий должен быть от 10 до 2000 символов")
    private String comment;
}
