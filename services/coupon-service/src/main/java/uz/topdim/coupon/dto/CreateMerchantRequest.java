package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * DTO запроса на создание/обновление партнёра.
 * Поддерживает как legacy поля (address, phone), так и normalized locations.
 */
@Data
public class CreateMerchantRequest {
    @NotBlank(message = "Название партнёра обязательно")
    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;

    // Legacy contact fields (still accepted; auto-creates primary location if no locations provided)
    private String address;
    private String phone;
    private String email;
    private String website;
    private String workingHours;
    private String contactPerson;

    /** Normalized locations. If provided, these take priority over legacy address/phone/workingHours. */
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
