package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO краткой информации о мерчанте для списка в админке.
 * Включает readiness-статус и счётчики купонов.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMerchantSummaryResponse {
    private Long id;
    private String name;
    private String logoUrl;
    private String contactPerson;
    private String email;
    private Long userId;
    private boolean active;

    /** Primary location snapshot for quick display */
    private MerchantLocationResponse primaryLocation;

    /** Publication readiness */
    private boolean publicationReady;
    private String publicationBlockReason;

    /** Coupon counters */
    private long activeCouponsCount;
    private long waitingCouponsCount;
    private long totalCouponsCount;
}
