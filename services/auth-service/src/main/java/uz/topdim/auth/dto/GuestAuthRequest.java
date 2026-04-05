package uz.topdim.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * DTO запроса гостевой аутентификации.
 * Создаёт пользователя с ролью GUEST без пароля.
 */
@Data
public class GuestAuthRequest {
    @NotBlank(message = "Номер телефона обязателен")
    private String phone;

    @NotBlank(message = "Имя обязательно")
    private String name;
}
