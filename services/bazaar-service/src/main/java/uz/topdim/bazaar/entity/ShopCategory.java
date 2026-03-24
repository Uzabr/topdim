package uz.topdim.bazaar.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "shop_categories")
@Getter @Setter @Builder @NoArgsConstructor /**
 * Категория магазина.
 * Поля: name, slug (electronics, clothing, food и т.д.).
 */
@AllArgsConstructor
public class ShopCategory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(name = "name_uz")
    private String nameUz;

    private String slug;

    @Column(name = "icon_url")
    private String iconUrl;

    private boolean active;
}
