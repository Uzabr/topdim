package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Сущность купонного предложения.
 * Основная бизнес-сущность: title, описание, цены, скидка, статус.
 * Связана с CouponOption, CouponImage, Category, Merchant.
 */
@Entity
@Table(name = "coupon_offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    /** Canonical offer text (replaces legacy short_description + full_description + terms + usageRules + howToUse). */
    @Column(name = "offer_description", columnDefinition = "TEXT")
    private String offerDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(name = "old_price", precision = 12, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "from_price", precision = 12, scale = 2)
    private BigDecimal fromPrice;

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "buy_until")
    private LocalDateTime buyUntil;

    @Column(name = "use_until")
    private LocalDateTime useUntil;

    // Legacy contact fields (address, contact_phone, working_hours) removed — live in merchant_locations

    @Column(name = "is_gift_available")
    private boolean giftAvailable;

    @Column(name = "assigned_moderator_id")
    private Long assignedModeratorId;

    @Column(name = "assigned_moderator_name")
    private String assignedModeratorName;

    @Column(name = "revision_comment", columnDefinition = "TEXT")
    private String revisionComment;

    @Column(name = "archive_reason", columnDefinition = "TEXT")
    private String archiveReason;

    @Column(name = "archived_at")
    private LocalDateTime archivedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "total_sold")
    private int totalSold;

    @Column(name = "redeemed_count")
    private int redeemedCount;

    @Column(name = "view_count")
    private int viewCount;

    @Column(name = "total_turnover", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal totalTurnover = BigDecimal.ZERO;

    @OneToMany(mappedBy = "couponOffer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CouponOption> options = new ArrayList<>();

    @OneToMany(mappedBy = "couponOffer", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CouponImage> images = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
