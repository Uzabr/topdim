package uz.topdim.coupon.dto.merchantprofile;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WithdrawMerchantProfileChangeRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
