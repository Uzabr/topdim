package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO ответа партнёра.
 * Включает профиль, legacy contact fields и normalized locations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantResponse {
    private Long id;
    private String name;
    private String description;
    private String logoUrl;
    private String coverUrl;

    // Legacy contact fields: DEPRECATED. Will be removed in Step 4.
    // Contact data now lives exclusively in primaryLocation / locations.
    @Deprecated(forRemoval = true)
    private String address;
    @Deprecated(forRemoval = true)
    private String phone;
    private String email;
    private String website;
    @Deprecated(forRemoval = true)
    private String workingHours;
    private String contactPerson;

    private boolean active;

    /** Primary location (canonical source for storefront coupon detail). */
    private MerchantLocationResponse primaryLocation;

    /** All active locations (for admin merchant form). */
    private List<MerchantLocationResponse> locations;
}
