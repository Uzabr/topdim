package uz.topdim.coupon.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopResponse {
    private Long id;
    private String name;
    private String description;
    private String category;
    private String subcategory;
    private String goodsDescription;
    private String phone;
    private String workingHours;
    private List<String> photos;
    private String locationType;

    // Bazaar info (when locationType=BAZAAR)
    private BazaarSummary bazaar;
    private String pavilion;
    private String sector;
    private String rowNumber;
    private String shopNumber;
    private Integer floorNumber;

    // Standalone info (when locationType=STANDALONE)
    private String address;
    private double latitude;
    private double longitude;

    private String status;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BazaarSummary {
        private Long id;
        private String name;
    }
}
