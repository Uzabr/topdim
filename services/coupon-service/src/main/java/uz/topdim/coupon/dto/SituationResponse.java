package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Публичный DTO ситуации.
 * API-поле «key» = DB-поле «slug» (конвенция: фронт обращается по ключу).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SituationResponse {
    private String key;
    private String title;
    private String titleUz;
    private String imageUrl;
    private long couponCount;
    private boolean featured;
    private int sortOrder;
}
