package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantContextResponse {
    private Long merchantId;
    private Long userId;
    private String name;
    private boolean active;
}
