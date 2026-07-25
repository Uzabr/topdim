import type { PurchasedCoupon } from '../../api/orders';

export interface CouponActions {
  canRefund: boolean;
  canComplain: boolean;
  canReview: boolean;
}

export function getCouponActions(
  status: PurchasedCoupon['status'],
  hasOpenComplaint: boolean,
  hasReview: boolean,
): CouponActions {
  return {
    canRefund: status === 'ACTIVE',
    canComplain: !hasOpenComplaint,
    canReview: status === 'USED' && !hasReview,
  };
}
