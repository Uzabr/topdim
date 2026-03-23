package uz.topdim.user.dto;

import jakarta.validation.constraints.Size;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    @Size(min = 1, max = 100, message = "Имя должно быть от 1 до 100 символов")
    private String firstName;

    @Size(max = 100, message = "Фамилия не должна превышать 100 символов")
    private String lastName;

    @Size(max = 20, message = "Номер телефона не должен превышать 20 символов")
    private String phone;

    private String avatarUrl;
}
