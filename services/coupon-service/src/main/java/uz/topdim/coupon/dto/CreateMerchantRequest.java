package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateMerchantRequest {
    @NotBlank(message = "Название партнёра обязательно")
    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;
    private String address;
    private String phone;
    private String email;
    private String website;
    private String workingHours;
    private String contactPerson;
}
