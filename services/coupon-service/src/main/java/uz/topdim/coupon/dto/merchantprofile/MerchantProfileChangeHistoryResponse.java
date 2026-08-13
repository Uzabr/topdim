package uz.topdim.coupon.dto.merchantprofile;

import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.time.LocalDateTime;

public record MerchantProfileChangeHistoryResponse(
        Long id,
        MerchantProfileChangeStatus previousStatus,
        MerchantProfileChangeStatus newStatus,
        Long actorUserId,
        String actorRole,
        String comment,
        LocalDateTime createdAt
) {
}
