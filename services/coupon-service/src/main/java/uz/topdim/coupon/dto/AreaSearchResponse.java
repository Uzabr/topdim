package uz.topdim.coupon.dto;

import lombok.*;
import java.util.List;

/**
 * Response for area search — returns both bazaars and shops found in a bounding box.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AreaSearchResponse {
    private List<BazaarResponse> bazaars;
    private List<ShopResponse> shops;
}
