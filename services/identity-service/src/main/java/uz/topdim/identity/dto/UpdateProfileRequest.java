package uz.topdim.identity.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Обновление профиля пользователя.
 * <p>Телефон здесь больше не меняется (T8a) — легаси-путь позволял сменить номер
 * в обход OTP-подтверждения владения. Единственный путь привязки/смены телефона теперь —
 * {@code POST /api/v1/auth/phone/link} (OTP-подтверждённый,
 * см. {@link uz.topdim.identity.service.AuthService#linkPhone(Long, String, String)}).
 */
@Data @NoArgsConstructor @AllArgsConstructor
public class UpdateProfileRequest {
    @Size(min = 1, max = 100, message = "Имя должно быть от 1 до 100 символов")
    private String firstName;
    @Size(max = 100, message = "Фамилия не должна превышать 100 символов")
    private String lastName;
    private String avatarUrl;
}
