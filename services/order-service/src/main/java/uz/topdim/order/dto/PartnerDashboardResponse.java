package uz.topdim.order.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO дашборда партнёра (Owner/Manager).
 * Содержит KPI карточки, последние погашения и статистику по филиалам.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerDashboardResponse {
    /** Общее количество уникальных купонных предложений */
    private long totalCoupons;
    /** Количество активных купонов */
    private long activeCoupons;
    /** Всего продано */
    private long totalSold;
    /** Всего погашено */
    private long totalRedeemed;
    /** Ожидает погашения (ACTIVE) */
    private long pendingRedemption;
    /** Просрочено */
    private long expired;
    /** Общая выручка */
    private BigDecimal totalRevenue;
    /** Последние погашения */
    private List<RecentRedemption> recentRedemptions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentRedemption {
        private String couponTitle;
        private String couponCode;
        private Long merchantLocationId;
        private String staffName;
        private String redeemMethod;
        private LocalDateTime redeemedAt;
    }
}
