package uz.topdim.coupon.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO для создания заявки на акцию от партнёра.
 * Отличие от CreateCouponOfferRequest (admin):
 * - Нет merchantId (определяется из X-User-Id)
 * - coverImageUrl необязателен
 * - imageUrls — опциональный список фото (макс. 5)
 * - options — список вариантов
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatePartnerCouponRequest {

    @NotBlank(message = "Название предложения обязательно")
    @Size(max = 200, message = "Название не более 200 символов")
    private String title;

    @NotNull(message = "Укажите категорию")
    private Long categoryId;

    @NotBlank(message = "Описание предложения обязательно")
    @Size(max = 5000, message = "Описание не более 5000 символов")
    private String offerDescription;

    @NotNull(message = "Укажите старую цену")
    @Positive(message = "Старая цена должна быть положительной")
    private BigDecimal oldPrice;

    @NotNull(message = "Укажите цену по предложению")
    @Positive(message = "Цена по предложению должна быть положительной")
    private BigDecimal fromPrice;

    @Min(value = 1, message = "Скидка должна быть от 1%")
    @Max(value = 99, message = "Скидка не более 99%")
    private Integer discountPercent;

    @NotNull(message = "Укажите срок покупки")
    @Future(message = "Срок покупки должен быть в будущем")
    private LocalDateTime buyUntil;

    @NotNull(message = "Укажите срок использования")
    @Future(message = "Срок использования должен быть в будущем")
    private LocalDateTime useUntil;

    private boolean giftAvailable;

    /** Обложка (необязательна при создании заявки). */
    private String coverImageUrl;

    /** Дополнительные фото (макс. 5, необязательны). */
    @Size(max = 5, message = "Можно загрузить до 5 фотографий")
    private List<String> imageUrls;

    /** Варианты акции (обязательно хотя бы один). */
    @NotNull(message = "Добавьте хотя бы один вариант предложения")
    @Size(min = 1, message = "Добавьте хотя бы один вариант предложения")
    @Valid
    private List<CreateCouponOptionRequest> options;
}
