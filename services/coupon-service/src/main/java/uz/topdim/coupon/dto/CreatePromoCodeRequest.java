package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePromoCodeRequest {
    
    @NotBlank(message = "Код обязателен")
    private String code;

    @NotNull(message = "Сумма/процент скидки обязательна")
    @Positive(message = "Скидка должна быть больше 0")
    private BigDecimal discountAmount;

    private boolean percentage;
    
    private Integer usageLimit;
    
    private LocalDateTime expiresAt;
}
