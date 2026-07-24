// @vitest-environment jsdom
import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PurchasedCoupon } from '../api/orders';
import ProfileDesktop from './ProfileDesktop';

const queryState = vi.hoisted(() => ({
  coupons: [] as PurchasedCoupon[],
  complaints: [] as Array<{ purchasedCouponId: number; status: string }>,
  reviews: [] as Array<{ couponOfferId: number }>,
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('@tanstack/react-query', () => ({
  useQuery: ({ queryKey }: { queryKey: string[] }) => {
    if (queryKey[0] === 'my-coupons') return { data: queryState.coupons, isLoading: false };
    if (queryKey[0] === 'my-complaints') return { data: queryState.complaints };
    if (queryKey[0] === 'my-reviews') return { data: queryState.reviews };
    return { data: [] };
  },
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: () => ({
    user: { id: 7, email: 'user@example.com', firstName: 'Ada', role: 'USER' },
    logout: vi.fn(),
    isAuthenticated: true,
  }),
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  useLocation: () => ({ pathname: '/ru/profile', search: '' }),
  useNavigate: () => vi.fn(),
}));

vi.mock('../hooks/useLocalePath', () => ({
  useLocalePath: () => (path: string) => `/ru${path}`,
}));

vi.mock('../utils/format', () => ({
  daysUntil: () => 1,
  formatDate: (value: string) => value,
  formatPrice: (value: number) => String(value),
}));

const activeCoupon: PurchasedCoupon = {
  id: 1,
  couponOfferId: 10,
  couponOptionId: 100,
  couponTitle: 'Active dinner',
  optionTitle: 'Dinner for two',
  couponCode: 'ACTIVE-1',
  qrToken: '',
  status: 'ACTIVE',
  purchasedAt: '2026-07-01T10:00:00Z',
  expiresAt: '2026-08-01T10:00:00Z',
};

function couponActions(title: string, selector: string) {
  const coupon = screen.getByText(title).closest(selector);
  expect(coupon).not.toBeNull();
  return within(coupon as HTMLElement);
}

describe('ProfileDesktop coupon actions', () => {
  afterEach(cleanup);

  beforeEach(() => {
    queryState.coupons = [
      activeCoupon,
      {
        ...activeCoupon,
        id: 2,
        couponOfferId: 20,
        couponTitle: 'Pending refund',
        couponCode: 'PENDING-2',
        status: 'REFUND_PENDING',
        expiresAt: '2026-09-01T10:00:00Z',
      },
      {
        ...activeCoupon,
        id: 3,
        couponOfferId: 30,
        couponTitle: 'Used without review',
        couponCode: 'USED-3',
        status: 'USED',
      },
      {
        ...activeCoupon,
        id: 4,
        couponOfferId: 40,
        couponTitle: 'Used with review',
        couponCode: 'USED-4',
        status: 'USED',
      },
      {
        ...activeCoupon,
        id: 5,
        couponOfferId: 50,
        couponTitle: 'Cancelled with complaint',
        couponCode: 'CANCELLED-5',
        status: 'CANCELLED',
      },
    ];
    queryState.complaints = [
      { purchasedCouponId: 1, status: 'PENDING' },
      { purchasedCouponId: 5, status: 'IN_REVIEW' },
    ];
    queryState.reviews = [{ couponOfferId: 40 }];
  });

  it('shows policy-allowed actions on each live coupon', () => {
    render(<ProfileDesktop />);

    const active = couponActions('Active dinner', '.ticket');
    expect(active.getByRole('button', { name: 'profile.refundMoney' })).toBeTruthy();
    expect(active.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(active.getByText('profile.complaintPending')).toBeTruthy();

    const refundPending = couponActions('Pending refund', '.active-coupon');
    expect(refundPending.queryByRole('button', { name: 'profile.refundShort' })).toBeNull();
    expect(refundPending.getByRole('button', { name: 'profile.complain' })).toBeTruthy();
    expect(refundPending.queryByText('profile.complaintPending')).toBeNull();
  });

  it('shows policy-allowed review and complaint controls on each archived coupon', () => {
    render(<ProfileDesktop />);

    const unreviewed = couponActions('Used without review', '.archive-row');
    expect(unreviewed.getByRole('button', { name: 'profile.archive.leaveReview' })).toBeTruthy();
    expect(unreviewed.getByRole('button', { name: 'profile.complain' })).toBeTruthy();

    const reviewed = couponActions('Used with review', '.archive-row');
    expect(reviewed.queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
    expect(reviewed.getByRole('button', { name: 'profile.complain' })).toBeTruthy();

    const cancelled = couponActions('Cancelled with complaint', '.archive-row');
    expect(cancelled.queryByRole('button', { name: 'profile.archive.leaveReview' })).toBeNull();
    expect(cancelled.queryByRole('button', { name: 'profile.complain' })).toBeNull();
    expect(cancelled.getByText('profile.complaintPending')).toBeTruthy();
  });
});
