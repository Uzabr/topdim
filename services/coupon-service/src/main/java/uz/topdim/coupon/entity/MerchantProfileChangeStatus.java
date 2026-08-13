package uz.topdim.coupon.entity;

import java.util.List;

public enum MerchantProfileChangeStatus {
    DRAFT,
    PENDING_REVIEW,
    IN_REVIEW,
    REVISION_REQUESTED,
    APPROVED,
    REJECTED,
    WITHDRAWN,
    OUTDATED;

    private static final List<MerchantProfileChangeStatus> ACTIVE_STATUSES = List.of(
            DRAFT,
            PENDING_REVIEW,
            IN_REVIEW,
            REVISION_REQUESTED
    );

    public static List<MerchantProfileChangeStatus> activeStatuses() {
        return ACTIVE_STATUSES;
    }
}
