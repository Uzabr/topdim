package uz.topdim.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO запроса добавления товара в корзину.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddToCartRequest {

    @NotNull(message = "couponOfferId обязателен")
    private Long couponOfferId;

    @NotNull(message = "couponOptionId обязателен")
    private Long couponOptionId;

    @NotBlank(message = "Название купона обязательно")
    private String couponTitle;

    @NotBlank(message = "Название опции обязательно")
    private String optionTitle;

    @NotNull(message = "Цена обязательна")
    private BigDecimal unitPrice;

    @Min(value = 1, message = "Минимальное количество: 1")
    private int quantity = 1;

    private boolean isGift = false;
    private String giftRecipientName;
    private String giftRecipientPhone;
}
