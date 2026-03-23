package uz.topdim.bazaar.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "bazaars")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Bazaar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "name_uz")
    private String nameUz;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BazaarType type;

    private String address;
    private String city;
    private Double latitude;
    private Double longitude;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "working_hours")
    private String workingHours;

    private String phone;
    private boolean active;

    @OneToMany(mappedBy = "bazaar", cascade = CascadeType.ALL)
    @Builder.Default
    private List<BazaarMap> maps = new ArrayList<>();

    @OneToMany(mappedBy = "bazaar", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Shop> shops = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
