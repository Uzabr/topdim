package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO ответа локации мерчанта.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantLocationResponse {
    private Long id;
    private String title;
    private String address;
    private String phone;
    private String workingHours;
    private Double latitude;
    private Double longitude;
    private boolean primary;
    private boolean active;
}
