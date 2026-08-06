import type {
  AdminCouponRow,
  CouponAction,
  StaffUser,
} from './types';

function hasAdminRole(user: StaffUser): boolean {
  return user.role === 'ADMIN' || user.role === 'SUPER_ADMIN';
}

export function allowedCouponActions(
  coupon: AdminCouponRow,
  user: StaffUser,
): CouponAction[] {
  const admin = hasAdminRole(user);

  switch (coupon.status) {
    case 'LEAD':
      return ['view', 'take-to-work'];
    case 'DRAFT':
    case 'REVISION_REQUESTED':
      if (admin || coupon.assignedModeratorId === user.id) {
        return ['view', 'edit', 'send-to-approval'];
      }
      return ['view'];
    case 'WAITING_FOR_MERCHANT':
      return admin ? ['view', 'support-review'] : ['view'];
    case 'ACTIVE':
      return admin ? ['view', 'pause'] : ['view'];
    case 'PAUSED':
      return admin ? ['view', 'restore', 'archive'] : ['view'];
    case 'SOLD_OUT':
      return admin ? ['view', 'archive'] : ['view'];
    case 'ARCHIVED':
      return ['view'];
  }
}
