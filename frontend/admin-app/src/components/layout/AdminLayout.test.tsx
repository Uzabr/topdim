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

  it('shows action journal to ADMIN and SUPER_ADMIN only', () => {
    const forAdmin = flatten(filterMenuByRole(allMenuItems, 'ADMIN'))
      .find((item) => item.key === '/system/audit');
    const forSuper = flatten(filterMenuByRole(allMenuItems, 'SUPER_ADMIN'))
      .find((item) => item.key === '/system/audit');
    const forModerator = flatten(filterMenuByRole(allMenuItems, 'MODERATOR'))
      .find((item) => item.key === '/system/audit');

    expect(forAdmin?.label).toBe('Журнал действий');
    expect(forSuper?.label).toBe('Журнал действий');
    expect(forModerator).toBeUndefined();
  });

  it('keeps staff management exclusive to SUPER_ADMIN', () => {
    expect(flatten(filterMenuByRole(allMenuItems, 'ADMIN'))
      .find((item) => item.key === '/system/staff')).toBeUndefined();
    expect(flatten(filterMenuByRole(allMenuItems, 'SUPER_ADMIN'))
      .find((item) => item.key === '/system/staff')?.label).toBe('Сотрудники');
  });
});
