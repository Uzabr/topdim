package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Базар / торговый комплекс.
 * Содержит список магазинов ({@link Shop}).
 */
@Entity
@Table(name = "bazaars")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Bazaar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_uz")
    private String nameUz;

    @Column(nullable = false)
    private String type; // BAZAAR, SHOPPING_CENTER, MARKET, TRADE_COMPLEX

    private String description;

    private String address;

    private String city;

    private double latitude;

    private double longitude;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "working_hours")
    private String workingHours;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private BazaarStatus status = BazaarStatus.ACTIVE;

    @OneToMany(mappedBy = "bazaar", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Shop> shops = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
