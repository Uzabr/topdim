package uz.topdim.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import uz.topdim.identity.validation.StrongPassword;

@Data
public class CreateStaffRequest {
    @NotBlank(message = "Имя обязательно")
    private String name;
    @NotBlank(message = "Телефон обязателен")
    private String phone;
    private String role = "CASHIER";

    /** Email для самостоятельного логина сотрудника */
    @NotBlank(message = "Email для входа обязателен")
    @Email(message = "Некорректный email для входа")
    private String loginEmail;
    /** Временный пароль для самостоятельного логина сотрудника */
    @NotBlank(message = "Временный пароль обязателен")
    @StrongPassword
    private String temporaryPassword;
    /** ID филиала (обязателен для CASHIER) */
    private Long merchantLocationId;
}
