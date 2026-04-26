package uz.topdim.coupon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateMerchantOnboardingRequest {
    @NotNull(message = "userId is required")
    private Long userId;

    @NotBlank(message = "merchant name is required")
    private String name;

    private String description;
    private String email;
    private String website;
    private String contactPerson;

    @Valid
    @NotNull(message = "location is required")
    private Location location;

    @Data
    public static class Location {
        private String title;

        @NotBlank(message = "address is required")
        private String address;

        @NotBlank(message = "phone is required")
        private String phone;

        private String workingHours;
        private Double latitude;
        private Double longitude;
    }
}
