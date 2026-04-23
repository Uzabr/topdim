package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO ответа купонного предложения.
 * Включает: id, title, prices, discount, status, merchant (с primaryLocation), images.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponOfferResponse {
    private Long id;
    private String title;

    /** Canonical offer text shown in storefront and admin flows. */
    private String offerDescription;

    private MerchantSummary merchant;
    private CategorySummary category;
    private BigDecimal oldPrice;
    private BigDecimal fromPrice;
    private Integer discountPercent;
    private String coverImageUrl;
    private LocalDateTime buyUntil;
    private LocalDateTime useUntil;
    private boolean giftAvailable;
    private String status;
    private Long assignedModeratorId;
    private String assignedModeratorName;
    private String revisionComment;
    private int totalSold;
    private int redeemedCount;
    private BigDecimal totalTurnover;
    private int viewCount;
    private Double averageRating;
    private int reviewCount;
    private List<CouponOptionResponse> options;
    private List<String> images;
    private LocalDateTime createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MerchantSummary {
        private Long id;
        private String name;
        private String logoUrl;
        private String description;
        private MerchantLocationResponse primaryLocation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategorySummary {
        private Long id;
        private String name;
        private String slug;
        private String iconUrl;
    }
}
