package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CreateCouponOfferRequest {

    @NotBlank(message = "Название обязательно")
    private String title;

    private String shortDescription;
    private String fullDescription;

    @NotNull(message = "Укажите партнёра")
    private Long merchantId;

    @NotNull(message = "Укажите категорию")
    private Long categoryId;

    private BigDecimal oldPrice;

    @NotNull(message = "Укажите цену от")
    @Positive(message = "Цена должна быть положительной")
    private BigDecimal fromPrice;

    private Integer discountPercent;
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
}
