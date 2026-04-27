package uz.topdim.identity.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** DTO для merchant location из coupon-service. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MerchantLocationResponse {
    private Long id;
    private String title;
    private String address;
    private String phone;
    private String workingHours;
    private boolean primary;
    private boolean active;
}
