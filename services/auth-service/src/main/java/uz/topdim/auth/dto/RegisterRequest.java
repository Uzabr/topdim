package uz.topdim.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import uz.topdim.auth.validation.StrongPassword;

/**
 * DTO запроса на регистрацию.
 * Поля: email, phone, password, firstName, lastName.
 *
 * <p>Валидация:
 * <ul>
 *   <li>Email: формат RFC</li>
 *   <li>Phone: формат Узбекистан +998XXXXXXXXX (опциональный)</li>
 *   <li>Password: 8+ символов, заглавная, цифра, спецсимвол + blocklist</li>
 * </ul>
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Email обязателен")
    @Email(message = "Неверный формат email")
    private String email;

    @Pattern(
            regexp = "^\\+998[0-9]{9}$",
            message = "Номер телефона должен быть в формате +998XXXXXXXXX"
    )
    private String phone;

    @NotBlank(message = "Пароль обязателен")
    @StrongPassword
    private String password;

    @NotBlank(message = "Имя обязательно")
    private String firstName;

    private String lastName;
}
