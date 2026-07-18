package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Admin DTO ситуации: включает id и active, чтобы админка могла полноценно читать CRUD-сущность.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminSituationResponse {
    private Long id;
    private String key;
    private String title;
    private String titleUz;
    private String imageUrl;
    private long couponCount;
    private boolean featured;
    private int sortOrder;
    private boolean active;
}
