package uz.topdim.coupon.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * DTO статистики для Telegram-бота и админки.
 */
@Data
@Builder
public class CouponStatsResponse {
    private Long id;
    private String title;
    private String status;
    private int quantityLimit; // Сумма лимитов всех опций
    
    private int viewCount;
    private int totalSold;
    private int redeemedCount;
    private BigDecimal totalTurnover;
    
    private Double averageRating;
    private int reviewCount;
}
