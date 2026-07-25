// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { PurchasedCoupon } from '../../api/orders';
import ActiveCouponCard from './ActiveCouponCard';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({ t: (key: string) => key }),
}));

vi.mock('../../utils/format', () => ({
  formatDate: (value: string) => value,
}));

const coupon: PurchasedCoupon = {
  id: 2,
  couponOfferId: 20,
  couponOptionId: 200,
  couponTitle: 'Massage',
  optionTitle: 'One hour',
  couponCode: 'MASSAGE-2',
  qrToken: '',
  status: 'ACTIVE',
  purchasedAt: '2026-07-02T10:00:00Z',
};

describe('ActiveCouponCard actions', () => {
  afterEach(cleanup);

  it('shows refund and complaint for an active coupon without an open complaint', () => {
    render(
      <ActiveCouponCard
        coupon={coupon}
        hasComplaint={false}
        onRefund={vi.fn()}
        onComplain={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'profile.refundShort' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'profile.complain' })).toBeTruthy();
  });

  it('keeps refund but replaces complaint with the pending indicator', () => {
    render(
      <ActiveCouponCard
        coupon={coupon}
        hasComplaint
        onRefund={vi.fn()}
        onComplain={vi.fn()}
      />,
    );

    expect(screen.getByRole('button', { name: 'profile.refundShort' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(screen.getByText('profile.complaintPending')).toBeTruthy();
  });

  it('shows complaint but not refund while a refund is pending', () => {
    render(
      <ActiveCouponCard
        coupon={{ ...coupon, status: 'REFUND_PENDING' }}
        hasComplaint={false}
        onRefund={vi.fn()}
        onComplain={vi.fn()}
      />,
    );

    expect(screen.queryByRole('button', { name: 'profile.refundShort' })).toBeNull();
    expect(screen.getByRole('button', { name: 'profile.complain' })).toBeTruthy();
  });
});
