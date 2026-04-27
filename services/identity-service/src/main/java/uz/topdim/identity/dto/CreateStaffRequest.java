package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStaffRequest {
    @NotBlank(message = "Имя обязательно")
    private String name;
    @NotBlank(message = "Телефон обязателен")
    private String phone;
    private String role = "CASHIER";

    /** Email для самостоятельного логина кассира */
    private String loginEmail;
    /** Временный пароль для кассира */
    private String temporaryPassword;
    /** ID филиала (обязателен для CASHIER) */
    private Long merchantLocationId;
}
