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

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "full_description", columnDefinition = "TEXT")
    private String fullDescription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "merchant_id", nullable = false)
    private Merchant merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
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

    @Column(columnDefinition = "TEXT")
    private String terms;

    @Column(name = "usage_rules", columnDefinition = "TEXT")
    private String usageRules;

    @Column(name = "how_to_use", columnDefinition = "TEXT")
    private String howToUse;

    private String address;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "working_hours")
    private String workingHours;

    @Column(name = "is_gift_available")
    private boolean giftAvailable;

    @Column(name = "assigned_moderator_id")
    private Long assignedModeratorId;

    @Column(name = "assigned_moderator_name")
    private String assignedModeratorName;

    @Column(name = "revision_comment", columnDefinition = "TEXT")
    private String revisionComment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CouponStatus status;

    @Column(name = "total_sold")
    private int totalSold;

    @Column(name = "view_count")
    private int viewCount;

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
