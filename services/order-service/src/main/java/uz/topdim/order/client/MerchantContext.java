package uz.topdim.order.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantContext {
    private Long merchantId;
    private Long userId;
    private String name;
    private boolean active;
}
