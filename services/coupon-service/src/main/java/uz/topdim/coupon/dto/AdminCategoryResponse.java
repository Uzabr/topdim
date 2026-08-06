package uz.topdim.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Полная модель категории для административного интерфейса. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminCategoryResponse {
    private Long id;
    private String name;
    private String nameUz;
    private String slug;
    private String iconUrl;
    private int sortOrder;
    private boolean active;
}
