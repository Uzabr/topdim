// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PurchasedCoupon } from '../../api/orders';
import CouponTicket from './CouponTicket';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../../utils/format', () => ({
  daysUntil: () => 1,
  formatDate: (value: string) => value,
  formatPrice: (value: number) => String(value),
}));

const coupon: PurchasedCoupon = {
  id: 1,
  couponOfferId: 10,
  couponOptionId: 100,
  couponTitle: 'Dinner',
  optionTitle: 'Dinner for two',
  couponCode: 'DINNER-1',
  qrToken: '',
  status: 'ACTIVE',
  purchasedAt: '2026-07-01T10:00:00Z',
};

describe('CouponTicket actions', () => {
  afterEach(cleanup);

  it('shows refund and complaint for an active coupon without an open complaint', () => {
    render(
      <CouponTicket
        coupon={coupon}
        hasComplaint={false}
        onRefund={vi.fn()}
        onComplain={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'profile.refundMoney' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'profile.complain' })).toBeTruthy();
  });

  it('keeps refund but replaces complaint with the pending indicator', () => {
    render(
      <CouponTicket
        coupon={coupon}
        hasComplaint
        onRefund={vi.fn()}
        onComplain={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'profile.refundMoney' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(screen.getByText('profile.complaintPending')).toBeTruthy();
  });

  it('shows complaint but not refund while a refund is pending', () => {
    render(
      <CouponTicket
        coupon={{ ...coupon, status: 'REFUND_PENDING' }}
        hasComplaint={false}
        onRefund={vi.fn()}
        onComplain={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button', { name: 'profile.refundMoney' })).toBeNull();
    expect(screen.getByRole('button', { name: 'profile.complain' })).toBeTruthy();
  });
});
