package uz.topdim.identity.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailChangeRequest {
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String newEmail;
}
