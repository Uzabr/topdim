package uz.topdim.coupon.dto.merchantprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ModerationCommentRequest(
        @NotBlank(message = "Комментарий модератора обязателен")
        @Size(max = 2000, message = "Комментарий не должен превышать 2000 символов")
        String comment
) {
}
