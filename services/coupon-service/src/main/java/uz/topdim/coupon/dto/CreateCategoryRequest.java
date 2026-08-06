package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO для создания/обновления категории (Admin).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateCategoryRequest {

    @NotBlank(message = "Название категории обязательно")
    @Size(max = 100, message = "Название не более 100 символов")
    private String name;

    @Size(max = 100, message = "Название (uz) не более 100 символов")
    private String nameUz;

    @Size(max = 100, message = "Slug не более 100 символов")
    private String slug;

    @Size(max = 500, message = "URL иконки не более 500 символов")
    private String iconUrl;

    @Min(value = 0, message = "Порядок сортировки не может быть отрицательным")
    private int sortOrder;

    private boolean active = true;
}
