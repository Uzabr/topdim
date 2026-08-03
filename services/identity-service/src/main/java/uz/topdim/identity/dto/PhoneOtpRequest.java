package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Запрос кода подтверждения на телефон (T5: телефон-OTP как вход/регистрация/восстановление).
 * Endpoint всегда отвечает 202 независимо от того, существует ли аккаунт с этим номером
 * (anti-enumeration, см. AuthController#phoneOtpRequest).
 */
@Data
public class PhoneOtpRequest {

    @NotBlank(message = "Телефон обязателен")
    @Pattern(regexp = "^\\+998\\d{9}$", message = "Формат: +998XXXXXXXXX")
    private String phone;
}
