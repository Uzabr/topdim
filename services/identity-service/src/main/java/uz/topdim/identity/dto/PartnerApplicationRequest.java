package uz.topdim.identity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PartnerApplicationRequest {
    @NotBlank(message = "Имя обязательно") @Size(max = 50)
    private String firstName;
    @NotBlank(message = "Фамилия обязательна") @Size(max = 50)
    private String lastName;
    @NotBlank(message = "Телефон обязателен") @Size(max = 20)
    private String phone;
    @NotBlank(message = "Название компании обязательно") @Size(max = 100)
    private String companyName;
    private String comment;

    @Size(max = 255)
    private String email;

    @Size(max = 100)
    private String city;

    @Size(max = 500)
    private String address;

    @Size(max = 255)
    private String workingHours;

    @Size(max = 100)
    private String businessCategory;

    @Size(max = 255)
    private String website;

    @Size(max = 100)
    private String telegramUsername;
}
