package uz.topdim.coupon.dto.merchantprofile;

import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.time.LocalDateTime;

public record AdminMerchantProfileChangeFilter(
        MerchantProfileChangeStatus status,
        String search,
        Long assigneeUserId,
        LocalDateTime submittedFrom,
        LocalDateTime submittedTo
) {
}
