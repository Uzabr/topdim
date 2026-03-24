package uz.topdim.bazaar.dto;

import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO ответа базара.
 * Поля: id, name, address, lat, lng, shopCount.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BazaarResponse {
    private Long id;
    private String name;
    private String nameUz;
    private String type;
    private String address;
    private String city;
    private Double latitude;
    private Double longitude;
    private String description;
    private String coverImageUrl;
    private String workingHours;
    private String phone;
    private int shopCount;
    private int mapCount;
    private LocalDateTime createdAt;
}
