package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Подтверждение кода из SMS — вход/регистрация/восстановление одним путём
 * (см. {@link uz.topdim.identity.service.AuthService#phoneAuth(String, String)}).
 */
@Data
public class PhoneOtpConfirmRequest {

    @NotBlank(message = "Телефон обязателен")
    @Pattern(regexp = "^\\+998\\d{9}$", message = "Формат: +998XXXXXXXXX")
    private String phone;

    @NotBlank(message = "Код обязателен")
    private String code;
}
