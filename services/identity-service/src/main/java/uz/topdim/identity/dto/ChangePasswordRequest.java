package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import uz.topdim.identity.validation.StrongPassword;

@Data
public class ChangePasswordRequest {
    @NotBlank(message = "Текущий пароль обязателен")
    private String currentPassword;

    @NotBlank(message = "Новый пароль обязателен")
    @StrongPassword
    private String newPassword;

    @NotBlank(message = "Подтверждение пароля обязательно")
    private String confirmPassword;
}
