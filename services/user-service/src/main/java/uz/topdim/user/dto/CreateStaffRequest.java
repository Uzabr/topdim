package uz.topdim.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStaffRequest {
    @NotBlank(message = "Имя обязательно")
    private String name;

    @NotBlank(message = "Телефон обязателен")
    private String phone;

    private String role = "CASHIER"; // по умолчанию
}
