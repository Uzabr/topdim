package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponOptionResponse {
    private Long id;
    private String title;
    private BigDecimal regularPrice;
    private BigDecimal couponPrice;
    private Integer quantityLimit;
    private int quantitySold;
    private String status;
}
