package uz.topdim.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import uz.topdim.identity.validation.StrongPassword;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    private String email;

    private String phone;

    @NotBlank(message = "Пароль обязателен")
    @StrongPassword
    private String password;

    @NotBlank(message = "Имя обязательно")
    @Size(min = 1, max = 100, message = "Имя должно быть от 1 до 100 символов")
    private String firstName;

    @Size(max = 100)
    private String lastName;
}
