package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Партнёр (продавец купонов).
 * Поля: name, description, logoUrl, email, website, contactPerson.
 * Contact details (address, phone, workingHours) live in MerchantLocation.
 * Связан с CouponOffer и MerchantLocation.
 */
@Entity
@Table(name = "merchants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    // Legacy contact fields (address, phone, working_hours) removed — live in merchant_locations

    private String email;

    private String website;

    @Column(name = "contact_person")
    private String contactPerson;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(name = "telegram_chat_id")
    private String telegramChatId;

    @OneToMany(mappedBy = "merchant", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MerchantLocation> locations = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
