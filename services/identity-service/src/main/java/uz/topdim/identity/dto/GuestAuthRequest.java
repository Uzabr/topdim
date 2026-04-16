package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GuestAuthRequest {
    @NotBlank(message = "Телефон обязателен")
    private String phone;

    private String name;
}
