package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateShopRequest {

    private Long merchantId;

    @NotBlank
    private String name;

    private String description;
    private String category;
    private String subcategory;
    private String goodsDescription;
    private String phone;
    private String workingHours;
    private List<String> photos;

    @NotBlank
    private String locationType; // BAZAAR | STANDALONE

    private Long bazaarId;       // required if BAZAAR
    private String address;      // required if STANDALONE
    private double latitude;
    private double longitude;

    private String pavilion;
    private String sector;
    private String rowNumber;
    private String shopNumber;
    private Integer floorNumber;

    /** If not provided, defaults to ACTIVE. Set to PENDING_REVIEW for Telegram bot submissions. */
    private String status;
}
