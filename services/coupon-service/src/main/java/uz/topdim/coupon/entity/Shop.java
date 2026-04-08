package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Магазин в справочнике.
 * Поддерживает два типа размещения:
 * - BAZAAR: магазин внутри базара (с номером павильона, ряда, места).
 * - STANDALONE: отдельный магазин по адресу.
 *
 * merchantId подготовлен для будущей интеграции с Telegram-ботом,
 * через который мерчанты смогут регистрировать свои магазины.
 */
@Entity
@Table(name = "shops")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Связь с мерчантом (nullable). Для будущего Telegram-бота. */
    @Column(name = "merchant_id")
    private Long merchantId;

    @Column(nullable = false)
    private String name;

    private String description;

    private String category;

    private String subcategory;

    @Column(name = "goods_description")
    private String goodsDescription;

    private String phone;

    @Column(name = "working_hours")
    private String workingHours;

    /** JSON-массив URL фотографий. */
    @Column(columnDefinition = "TEXT")
    private String photos;

    // ═══ Location ═══

    @Enumerated(EnumType.STRING)
    @Column(name = "location_type", nullable = false)
    private LocationType locationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bazaar_id")
    private Bazaar bazaar;

    /** Адрес для STANDALONE магазинов. */
    private String address;

    private double latitude;

    private double longitude;

    // ═══ Bazaar-specific location ═══

    private String pavilion;

    private String sector;

    @Column(name = "row_number")
    private String rowNumber;

    @Column(name = "shop_number")
    private String shopNumber;

    @Column(name = "floor_number")
    private Integer floorNumber;

    // ═══ Status ═══

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ShopStatus status = ShopStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
