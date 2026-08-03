import { describe, expect, it } from 'vitest';
import type { UserRole } from '../../types';
import { allMenuItems, filterMenuByRole, type MenuItem } from './adminMenu';

const COUPON_MENU_KEYS = new Set([
  '/coupons',
  '/moderation/coupons',
  '/moderation/coupons/kanban',
  '/moderation/coupons/review',
  '/moderation/coupons/create',
  '/moderation/requests',
]);

function flatten(items: MenuItem[]): MenuItem[] {
  return items.flatMap((item) => [item, ...flatten(item.children ?? [])]);
}

describe('admin coupon navigation', () => {
  it.each<UserRole>(['MODERATOR', 'ADMIN', 'SUPER_ADMIN'])(
    'shows one canonical coupon entry to %s',
    (role) => {
      const couponEntries = flatten(filterMenuByRole(allMenuItems, role))
        .filter((item) => COUPON_MENU_KEYS.has(item.key));

      expect(couponEntries.map(({ key, label }) => ({ key, label }))).toEqual([
        { key: '/coupons', label: 'Купоны' },
      ]);
    },
  );
});
