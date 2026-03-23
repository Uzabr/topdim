package uz.topdim.bazaar.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shops")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Shop {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bazaar_id", nullable = false)
    private Bazaar bazaar;

    @Column(nullable = false)
    private String name;

    @Column(name = "row_number")
    private String rowNumber;

    @Column(name = "shop_number")
    private String shopNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private ShopCategory category;

    @Column(name = "goods_description", columnDefinition = "TEXT")
    private String goodsDescription;

    @Column(name = "working_hours")
    private String workingHours;

    private String phone;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "has_coupon")
    private boolean hasCoupon;

    @Column(name = "linked_coupon_offer_id")
    private Long linkedCouponOfferId;

    @Column(name = "floor_number")
    private int floorNumber;

    @Column(name = "zone_id")
    private String zoneId;

    private boolean active;

    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ShopProductTag> productTags = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
