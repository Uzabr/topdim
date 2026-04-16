package uz.topdim.coupon.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BazaarResponse {
    private Long id;
    private String name;
    private String nameUz;
    private String type;
    private String description;
    private String address;
    private String city;
    private double latitude;
    private double longitude;
    private String coverImageUrl;
    private String workingHours;
    private String phone;
    private String status;
    private int shopCount;
}
