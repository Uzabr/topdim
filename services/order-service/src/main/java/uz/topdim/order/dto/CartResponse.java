package uz.topdim.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Storefront-safe DTO ответа корзины.
 * Не содержит JPA-аннотаций и внутренних связей.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private Long id;
    private Long userId;
    private List<CartItemResponse> items;
    private BigDecimal totalAmount;
    private int totalItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CartItemResponse {
        private Long id;
        private Long couponOfferId;
        private Long couponOptionId;
        private String couponTitle;
        private String optionTitle;
        private BigDecimal unitPrice;
        private int quantity;
        private BigDecimal subtotal;
        private boolean gift;
        private String giftRecipientName;
        private String giftRecipientPhone;
    }
}
