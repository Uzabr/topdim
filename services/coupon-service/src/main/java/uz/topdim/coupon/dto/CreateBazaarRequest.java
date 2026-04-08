package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateBazaarRequest {

    @NotBlank
    private String name;

    private String nameUz;

    @NotBlank
    private String type;

    private String description;
    private String address;
    private String city;
    private double latitude;
    private double longitude;
    private String coverImageUrl;
    private String workingHours;
    private String phone;
}
