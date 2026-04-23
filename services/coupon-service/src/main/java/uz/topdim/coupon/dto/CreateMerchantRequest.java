package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * DTO запроса на создание/обновление партнёра.
 * Contact data is managed only through normalized locations.
 */
@Data
public class CreateMerchantRequest {
    @NotBlank(message = "Название партнёра обязательно")
    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;

    private String email;
    private String website;
    private String contactPerson;

    /** Normalized locations. Empty list is allowed for incomplete merchants before publication. */
    private List<LocationRequest> locations;

    @Data
    public static class LocationRequest {
        private Long id; // null for new locations
        private String title;
        private String address;
        private String phone;
        private String workingHours;
        private Double latitude;
        private Double longitude;
        private boolean primary;
    }
}
