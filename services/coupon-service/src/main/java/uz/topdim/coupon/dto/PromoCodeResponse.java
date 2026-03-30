package uz.topdim.coupon.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PromoCodeResponse {
    private Long id;
    private String code;
    private BigDecimal discountAmount;
    private boolean percentage;
    private Integer usageLimit;
    private int usedCount;
    private LocalDateTime expiresAt;
    private boolean active;
    private LocalDateTime createdAt;
}
