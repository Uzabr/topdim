package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Canonical contract for coupon create/update flows.
 */
@Data
public class CreateCouponOfferRequest {

    @NotBlank(message = "Название обязательно")
    private String title;

    @NotBlank(message = "Описание оффера обязательно")
    private String offerDescription;

    @NotNull(message = "Партнер обязателен")
    private Long merchantId;

    @NotNull(message = "Укажите категорию")
    private Long categoryId;

    private BigDecimal oldPrice;

    @NotNull(message = "Укажите цену от")
    @Positive(message = "Цена должна быть положительной")
    private BigDecimal fromPrice;

    private Integer discountPercent;
    @NotBlank(message = "URL изображения обязателен")
    private String coverImageUrl;

    @NotNull(message = "Укажите срок покупки")
    private LocalDateTime buyUntil;

    @NotNull(message = "Укажите срок использования")
    private LocalDateTime useUntil;

    private boolean giftAvailable;

    private List<CreateCouponOptionRequest> options;

    /** Дополнительные фотографии для галереи (URL-адреса). */
    private List<String> images;
}
