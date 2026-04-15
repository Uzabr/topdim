package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import uz.topdim.identity.validation.StrongPassword;

@Data
public class PasswordResetConfirmRequest {
    @NotBlank(message = "Токен обязателен")
    private String token;

    @NotBlank(message = "Новый пароль обязателен")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "Подтверждение пароля обязательно")
    private String confirmPassword;
}
