package uz.topdim.coupon.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "categories")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Category {

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

    @Column(name = "sort_order")
    private int sortOrder;

    private boolean active;
}
