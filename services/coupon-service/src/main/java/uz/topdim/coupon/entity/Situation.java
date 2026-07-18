package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Ситуация (кураторская подборка купонов для главной).
 * Поля: slug (стабильный ключ), title (ru), titleUz, imageUrl, featured, sortOrder, active.
 * Кэшируется в Redis (глобальный TTL 5 мин).
 */
@Entity
@Table(name = "situations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Situation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "title_uz", length = 128)
    private String titleUz;

    @Column(name = "image_url", length = 512)
    private String imageUrl;

    @Column(nullable = false)
    private boolean featured;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private boolean active;
}
