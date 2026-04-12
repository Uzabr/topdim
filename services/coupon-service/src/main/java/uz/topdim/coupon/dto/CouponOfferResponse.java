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
 * Включает: id, title, prices, discount, status, merchant, images.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponOfferResponse {
    private Long id;
    private String title;
    private String shortDescription;
    private String fullDescription;
    private MerchantSummary merchant;
    private CategorySummary category;
    private BigDecimal oldPrice;
    private BigDecimal fromPrice;
    private Integer discountPercent;
    private String coverImageUrl;
    private LocalDateTime buyUntil;
    private LocalDateTime useUntil;
    private String terms;
    private String usageRules;
    private String howToUse;
    private String address;
    private String contactPhone;
    private String workingHours;
    private boolean giftAvailable;
    private String status;
    private Long assignedModeratorId;
    private String assignedModeratorName;
    private String revisionComment;
    private int totalSold;
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
