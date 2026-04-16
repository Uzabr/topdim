package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmailConfirmRequest {
    @NotBlank(message = "Токен обязателен")
    private String token;
}
