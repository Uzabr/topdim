package uz.topdim.coupon.dto.merchantprofile;

import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.time.LocalDateTime;

public record MerchantProfileChangeSummary(
        Long id,
        Long merchantId,
        String name,
        MerchantProfileChangeStatus status,
        long baseProfileVersion,
        Long authorUserId,
        Long authorStaffId,
        String authorRole,
        Long assigneeUserId,
        String moderationComment,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime submittedAt,
        LocalDateTime assignedAt
) {
}
