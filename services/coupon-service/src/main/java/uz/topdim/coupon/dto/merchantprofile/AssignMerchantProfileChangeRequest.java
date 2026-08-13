package uz.topdim.coupon.dto.merchantprofile;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AssignMerchantProfileChangeRequest(
        @NotNull(message = "Укажите исполнителя")
        @Positive(message = "ID исполнителя должен быть положительным")
        Long assigneeUserId
) {
}
