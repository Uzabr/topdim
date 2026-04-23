package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO ответа партнёра.
 * Contact data is exposed only through primaryLocation / locations.
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

    private String email;
    private String website;
    private String contactPerson;

    private boolean active;

    /** Primary location (canonical source for storefront coupon detail). */
    private MerchantLocationResponse primaryLocation;

    /** All active locations (for admin merchant form). */
    private List<MerchantLocationResponse> locations;
}
