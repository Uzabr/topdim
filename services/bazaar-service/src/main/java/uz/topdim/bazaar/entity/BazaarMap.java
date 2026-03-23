package uz.topdim.bazaar.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bazaar_maps")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BazaarMap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bazaar_id", nullable = false)
    private Bazaar bazaar;

    @Column(name = "floor_number")
    private int floorNumber;

    @Column(name = "floor_name")
    private String floorName;

    @Column(name = "map_image_url", nullable = false)
    private String mapImageUrl;

    @Column(name = "map_svg_url")
    private String mapSvgUrl;

    @Column(name = "zones_json", columnDefinition = "TEXT")
    private String zonesJson;
}
