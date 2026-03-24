package uz.topdim.bazaar.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.List;

/**
 * DTO запроса на создание магазина.
 * Поля: name, floor, section, phone, categoryId.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateShopRequest {
    @NotNull(message = "bazaarId обязателен")
    private Long bazaarId;
    @NotBlank(message = "Название обязательно")
    private String name;
    private String rowNumber;
    private String shopNumber;
    private Long categoryId;
    private String goodsDescription;
    private String workingHours;
    private String phone;
    private String photoUrl;
    private int floorNumber;
    private String zoneId;
    private List<String> productTags;
}
