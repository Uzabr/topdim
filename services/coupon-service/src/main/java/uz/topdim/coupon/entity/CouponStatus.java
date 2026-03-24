package uz.topdim.coupon.entity;

/**
 * Статусы купонного предложения.
 * DRAFT → ACTIVE → PAUSED → ENDED.
 */
public enum CouponStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    EXPIRED,
    ARCHIVED
}
