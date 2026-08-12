package uz.topdim.coupon.dto.merchantprofile;

import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.time.LocalDateTime;

public record MerchantProfileChangeSummary(
        Long id,
        String name,
        MerchantProfileChangeStatus status,
        long baseProfileVersion,
        Long authorUserId,
        Long authorStaffId,
        String authorRole,
        String moderationComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime submittedAt
) {
}
