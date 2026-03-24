package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO запроса на создание варианта купона.
 * Поля: name, originalPrice, couponPrice, quantityLimit.
 */
@Data
public class CreateCouponOptionRequest {

    @NotBlank(message = "Название варианта обязательно")
    private String title;

    @NotNull(message = "Укажите обычную цену")
    @Positive
    private BigDecimal regularPrice;

    @NotNull(message = "Укажите цену по купону")
    @Positive
    private BigDecimal couponPrice;

    private Integer quantityLimit;
}
