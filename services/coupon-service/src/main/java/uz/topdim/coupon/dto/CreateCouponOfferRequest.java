package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO запроса на создание купонного предложения.
 * Поля: title, description, merchantId, categoryId, options[].
 */
@Data
public class CreateCouponOfferRequest {

    @NotBlank(message = "Название обязательно")
    private String title;

    private String shortDescription;
    private String fullDescription;

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

    private String terms;
    private String usageRules;
    private String howToUse;
    private String address;
    private String contactPhone;
    private String workingHours;
    private boolean giftAvailable;

    private List<CreateCouponOptionRequest> options;

    /** Дополнительные фотографии для галереи (URL-адреса). */
    private List<String> images;
}
