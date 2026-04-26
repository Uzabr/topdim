package uz.topdim.identity.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMerchantOnboardingRequest {
    private Long userId;
    private String name;
    private String description;
    private String email;
    private String website;
    private String contactPerson;
    private Location location;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Location {
        private String title;
        private String address;
        private String phone;
        private String workingHours;
        private Double latitude;
        private Double longitude;
    }
}
