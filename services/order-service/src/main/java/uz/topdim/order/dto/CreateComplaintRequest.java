package uz.topdim.order.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateComplaintRequest {
    /**
     * Legacy: orderId. Новый flow отправляет purchasedCouponId,
     * а orderId выводится из purchasedCoupon.order.
     */
    private Long orderId;

    /**
     * Per-coupon complaint. Если передан, orderId игнорируется.
     */
    private Long purchasedCouponId;

    @NotBlank(message = "Тема обращения обязательна")
    private String subject;

    @NotBlank(message = "Описание проблемы обязательно")
    private String description;
}
