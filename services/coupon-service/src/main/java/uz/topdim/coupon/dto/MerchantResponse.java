package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO ответа партнёра.
 * Поля: id, name, description, logoUrl, address.
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
    private String address;
    private String phone;
    private String email;
    private String website;
    private String workingHours;
    private String contactPerson;
    private boolean active;
}
