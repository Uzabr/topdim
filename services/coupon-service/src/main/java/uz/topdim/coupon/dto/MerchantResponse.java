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

    // Legacy contact fields (kept for backward compat, Release 2 cleanup)
    private String address;
    private String phone;
    private String email;
    private String website;
    private String workingHours;
    private String contactPerson;

    private boolean active;

    /** Primary location (canonical source for storefront coupon detail). */
    private MerchantLocationResponse primaryLocation;

    /** All active locations (for admin merchant form). */
    private List<MerchantLocationResponse> locations;
}
