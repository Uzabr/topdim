package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для создания ситуации (Admin).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSituationRequest {

    @NotBlank(message = "Ключ (slug) обязателен")
    @Size(max = 64, message = "Ключ не более 64 символов")
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]*$", message = "Ключ должен содержать только латинские строчные буквы, цифры и дефисы")
    private String key;

    @NotBlank(message = "Название обязательно")
    @Size(max = 128, message = "Название не более 128 символов")
    private String title;

    @Size(max = 128, message = "Название (uz) не более 128 символов")
    private String titleUz;

    @Size(max = 512, message = "URL картинки не более 512 символов")
    private String imageUrl;

    private boolean featured;

    private int sortOrder;

    private boolean active = true;
}
