package uz.topdim.order.dto;

import lombok.*;
import java.math.BigDecimal;

/**
 * DTO статистики партнёра: продажи, погашения, выручка.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerStatsResponse {
    private long totalCoupons;
    private long totalSold;
    private long totalRedeemed;
    private BigDecimal totalRevenue;
}
