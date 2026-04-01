package uz.topdim.coupon.entity;

/**
 * Статусы купонного предложения.
 * DRAFT → ACTIVE → PAUSED → ENDED.
 */
public enum CouponStatus {
    DRAFT,
    PENDING_REVIEW,
    ACTIVE,
    REJECTED,
    PAUSED,
    EXPIRED,
    ARCHIVED
}
