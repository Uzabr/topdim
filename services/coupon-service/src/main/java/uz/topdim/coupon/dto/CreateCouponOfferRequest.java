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
 * Release 1: canonical field is offerDescription.
 * Legacy text fields accepted for backward compat but offerDescription takes priority.
 */
@Data
public class CreateCouponOfferRequest {

    @NotBlank(message = "Название обязательно")
    private String title;

    /** Canonical offer text (Release 1+). Takes priority over legacy fields. */
    private String offerDescription;

    // --- Legacy text fields (accepted for backward compat) ---
    private String shortDescription;
    private String fullDescription;
    private String terms;
    private String usageRules;
    private String howToUse;

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
