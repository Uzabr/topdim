package uz.topdim.coupon.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateMerchantActiveRequest {
    @NotNull(message = "Поле active обязательно")
    private Boolean active;
}
