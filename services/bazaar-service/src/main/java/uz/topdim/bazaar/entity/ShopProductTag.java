package uz.topdim.bazaar.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shop_product_tags")
@Getter @Setter @Builder @NoArgsConstructor /**
 * Тег продукта магазина.
 * Двуязычный: tag (ru) и tagUz (uz).
 * Связан с Shop.
 */
@AllArgsConstructor
public class ShopProductTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Column(nullable = false)
    private String tag;

    @Column(name = "tag_uz")
    private String tagUz;
}
