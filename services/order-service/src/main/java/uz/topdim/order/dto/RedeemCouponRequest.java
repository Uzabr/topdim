package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO запроса погашения купона (QR scan / ручной ввод).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RedeemCouponRequest {

    @NotBlank(message = "Код купона обязателен")
    private String couponCode;

    private Long merchantId;
    private String staffName;
}
