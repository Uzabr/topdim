package uz.topdim.bazaar.dto;

import lombok.Data;

/**
 * DTO для обновления магазина партнёром.
 * Только редактируемые поля: phone, workingHours, goodsDescription, photoUrl.
 */
@Data
public class UpdateShopRequest {
    private String phone;
    private String workingHours;
    private String goodsDescription;
    private String photoUrl;
}
