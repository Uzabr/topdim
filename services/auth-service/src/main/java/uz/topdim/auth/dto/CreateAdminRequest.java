package uz.topdim.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import uz.topdim.auth.entity.Role;

@Data
public class CreateAdminRequest {
    @NotBlank(message = "Email обязателен")
    @Email(message = "Неверный формат email")
    private String email;

    @NotBlank(message = "Имя обязательно")
    private String firstName;

    private String lastName;

    @NotBlank(message = "Телефон обязателен")
    private String phone;

    @NotBlank(message = "Пароль обязателен")
    private String password;

    private Role role; // ADMIN or MODERATOR or SUPER_ADMIN
}
