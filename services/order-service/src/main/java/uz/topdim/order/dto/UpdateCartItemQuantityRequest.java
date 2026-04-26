package uz.topdim.order.dto;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Запрос на обновление количества товара в корзине.
 * Используется в PATCH /api/v1/cart/items/{itemId}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCartItemQuantityRequest {

    @Min(value = 1, message = "Количество должно быть больше 0")
    private int quantity;
}
