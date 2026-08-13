package uz.topdim.coupon.client;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PartnerAccessContext {
    private String role;
    private Long merchantId;
    private Long merchantLocationId;
    private Long staffId;
    private String staffName;
    private boolean canViewDashboard;
    private boolean canRedeem;
}
