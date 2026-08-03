import { describe, expect, it } from 'vitest';
import { allowedCouponActions } from './permissions';
import type {
  AdminCouponRow,
  CouponAction,
  CouponStatus,
  StaffUser,
} from './types';

const moderator: StaffUser = { id: 7, role: 'MODERATOR' };
const admin: StaffUser = { id: 8, role: 'ADMIN' };
const superAdmin: StaffUser = { id: 9, role: 'SUPER_ADMIN' };

function coupon(
  status: CouponStatus,
  assignedModeratorId: number | null = null,
): AdminCouponRow {
  return {
    id: 42,
    title: 'Test coupon',
    status,
    assignedModeratorId,
    assignedModeratorName: null,
    merchant: null,
    oldPrice: null,
    fromPrice: 0,
    discountPercent: 0,
    buyUntil: null,
    useUntil: null,
    createdAt: '2026-08-03T00:00:00',
  };
}

function expectActions(
  status: CouponStatus,
  user: StaffUser,
  expected: CouponAction[],
  assignedModeratorId: number | null = null,
) {
  expect(allowedCouponActions(coupon(status, assignedModeratorId), user)).toEqual(expected);
}

describe('coupon workspace permission matrix', () => {
  it('allows every staff role to view and take a LEAD', () => {
    for (const user of [moderator, admin, superAdmin]) {
      expectActions('LEAD', user, ['view', 'take-to-work']);
    }
  });

  it.each(['DRAFT', 'REVISION_REQUESTED'] as const)(
    'allows a moderator to edit and send only their own %s coupon',
    (status) => {
      expectActions(status, moderator, ['view', 'edit', 'send-to-approval'], moderator.id);
      expectActions(status, moderator, ['view'], 99);
      expectActions(status, moderator, ['view'], null);
    },
  );

  it.each(['DRAFT', 'REVISION_REQUESTED'] as const)(
    'allows admin roles to edit and send any %s coupon',
    (status) => {
      expectActions(status, admin, ['view', 'edit', 'send-to-approval'], 99);
      expectActions(status, superAdmin, ['view', 'edit', 'send-to-approval'], null);
    },
  );

  it('keeps partner decisions unavailable to moderators', () => {
    expectActions('WAITING_FOR_MERCHANT', moderator, ['view'], moderator.id);
    expectActions('WAITING_FOR_MERCHANT', admin, ['view', 'support-review'], moderator.id);
    expectActions('WAITING_FOR_MERCHANT', superAdmin, ['view', 'support-review'], null);
  });

  it('allows publication lifecycle actions only to admin roles', () => {
    expectActions('ACTIVE', moderator, ['view']);
    expectActions('ACTIVE', admin, ['view', 'pause']);
    expectActions('ACTIVE', superAdmin, ['view', 'pause']);

    expectActions('PAUSED', moderator, ['view']);
    expectActions('PAUSED', admin, ['view', 'restore', 'archive']);
    expectActions('PAUSED', superAdmin, ['view', 'restore', 'archive']);

    expectActions('SOLD_OUT', moderator, ['view']);
    expectActions('SOLD_OUT', admin, ['view', 'archive']);
    expectActions('SOLD_OUT', superAdmin, ['view', 'archive']);
  });

  it('keeps archived coupons read-only for every staff role', () => {
    for (const user of [moderator, admin, superAdmin]) {
      expectActions('ARCHIVED', user, ['view']);
    }
  });

  it('never exposes delete or partner-request rejection as workspace actions', () => {
    const allCases = (['LEAD', 'DRAFT', 'REVISION_REQUESTED', 'WAITING_FOR_MERCHANT',
      'ACTIVE', 'PAUSED', 'SOLD_OUT', 'ARCHIVED'] as CouponStatus[])
      .flatMap((status) => [moderator, admin, superAdmin]
        .flatMap((user) => allowedCouponActions(coupon(status, user.id), user)));

    expect(allCases).not.toContain('delete');
    expect(allCases).not.toContain('reject-request');
  });
});
