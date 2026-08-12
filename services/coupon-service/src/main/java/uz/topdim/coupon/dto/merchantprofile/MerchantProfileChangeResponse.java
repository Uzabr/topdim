package uz.topdim.coupon.dto.merchantprofile;

import uz.topdim.coupon.entity.MerchantProfileChangeStatus;

import java.time.LocalDateTime;
import java.util.List;

public record MerchantProfileChangeResponse(
        Long id,
        Long merchantId,
        Long authorUserId,
        Long authorStaffId,
        String authorRole,
        long baseProfileVersion,
        MerchantProfileChangeStatus status,
        Long assigneeUserId,
        String moderationComment,
        String name,
        String description,
        String logoUrl,
        String coverUrl,
        String email,
        String website,
        String contactPerson,
        List<MerchantProfileLocationResponse> locations,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime submittedAt,
        LocalDateTime assignedAt,
        LocalDateTime decidedAt,
        LocalDateTime withdrawnAt,
        long lockVersion
) {
}
