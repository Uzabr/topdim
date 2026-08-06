package uz.topdim.coupon.dto;

import uz.topdim.coupon.entity.CouponStatus;

import java.util.Set;

public record AdminCouponFilter(
        Set<CouponStatus> statuses,
        String search,
        Long merchantId,
        Long assignedModeratorId
) {
}
