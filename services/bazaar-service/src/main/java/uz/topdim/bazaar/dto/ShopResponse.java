package uz.topdim.bazaar.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShopResponse {
    private Long id;
    private Long bazaarId;
    private String bazaarName;
    private String name;
    private String rowNumber;
    private String shopNumber;
    private String categoryName;
    private String goodsDescription;
    private String workingHours;
    private String phone;
    private String photoUrl;
    private boolean hasCoupon;
    private Long linkedCouponOfferId;
    private int floorNumber;
    private String zoneId;
    private List<String> productTags;
    private LocalDateTime createdAt;
}
