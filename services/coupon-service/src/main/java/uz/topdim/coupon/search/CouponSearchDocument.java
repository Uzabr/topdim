package uz.topdim.coupon.search;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Elasticsearch документ для купона (CQRS Read Model).
 * Синхронизируется с PostgreSQL при создании/обновлении купона.
 * Обеспечивает мгновенный полнотекстовый поиск по каталогу.
 *
 * Index: coupon_offers
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponSearchDocument {

    /** ID купона (совпадает с PostgreSQL) */
    private Long id;

    /** Название (индексируется для поиска) */
    private String title;

    /** Краткое описание (индексируется) */
    private String shortDescription;

    /** Полное описание (индексируется) */
    private String fullDescription;

    /** Название категории */
    private String categoryName;

    /** Slug категории */
    private String categorySlug;

    /** ID категории */
    private Long categoryId;

    /** Название партнёра */
    private String merchantName;

    /** ID партнёра */
    private Long merchantId;

    /** Цена без скидки */
    private BigDecimal oldPrice;

    /** Цена по купону */
    private BigDecimal fromPrice;

    /** Процент скидки */
    private Integer discountPercent;

    /** URL обложки */
    private String coverImageUrl;

    /** Статус: ACTIVE, PAUSED, ENDED */
    private String status;

    /** Количество проданных */
    private Integer totalSold;

    /** Адрес */
    private String address;

    /** Дата создания */
    private LocalDateTime createdAt;

    /** Купить до */
    private LocalDateTime buyUntil;

    /** Использовать до */
    private LocalDateTime useUntil;
}
