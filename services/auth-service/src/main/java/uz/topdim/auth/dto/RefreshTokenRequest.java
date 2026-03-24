package uz.topdim.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO запроса обновления/отзыва токена.
 * Содержит refreshToken для refresh и logout операций.
 */
@Data
public class RefreshTokenRequest {
    @NotBlank(message = "Refresh token обязателен")
    private String refreshToken;
}
