package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO для назначения купонов ситуации (Admin).
 * Полная замена набора: передаём весь список couponId.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SituationCouponRequest {

    @NotEmpty(message = "Список купонов не может быть пустым")
    private List<@NotNull Long> couponIds;
}
