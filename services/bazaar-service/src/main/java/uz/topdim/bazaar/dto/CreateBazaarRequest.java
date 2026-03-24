package uz.topdim.bazaar.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * DTO запроса на создание базара.
 * Поля: name, address, lat, lng, description.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateBazaarRequest {
    @NotBlank(message = "Название обязательно")
    private String name;
    private String nameUz;
    @NotBlank(message = "Тип обязателен")
    private String type;
    private String address;
    private String city;
    private Double latitude;
    private Double longitude;
    private String description;
    private String coverImageUrl;
    private String workingHours;
    private String phone;
}
