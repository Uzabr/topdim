import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { PurchasedCouponLookupPage } from './PurchasedCouponLookupPage';
import { lookupPurchasedCoupon, type AdminPurchasedCouponLookup } from './api';

vi.mock('./api', () => ({
  lookupPurchasedCoupon: vi.fn(),
}));

const lookupResult: AdminPurchasedCouponLookup = {
  purchasedCouponId: 51,
  orderId: 11,
  userId: 7,
  couponOfferId: 21,
  couponOptionId: 31,
  couponTitle: 'SPA для двоих',
  optionTitle: '90 минут',
  couponCode: 'CP-ABCD1234',
  status: 'ACTIVE',
  merchantId: 41,
  merchantName: 'SPA Oasis',
  merchantAddress: 'Ташкент, ул. Амира Темура, 1',
  purchasedAt: '2026-08-04T10:30:00',
  expiresAt: '2026-09-04T10:30:00',
  usedAt: null,
};

describe('PurchasedCouponLookupPage', () => {
  beforeEach(() => {
    vi.mocked(lookupPurchasedCoupon).mockResolvedValue(lookupResult);
  });

  it('finds a coupon and clears the stale result as soon as another code is entered', async () => {
    const user = userEvent.setup();
    render(<PurchasedCouponLookupPage />);
    const input = screen.getByPlaceholderText('Введите код купона (CP-XXXX1234)');

    await user.type(input, 'cp-abcd1234');
    await user.click(screen.getByRole('button', { name: /Найти/ }));

    expect(await screen.findByText('SPA для двоих')).toBeTruthy();
    expect(lookupPurchasedCoupon).toHaveBeenCalledWith('CP-ABCD1234');

    await user.clear(input);
    await user.type(input, 'cp-ffff0000');

    expect(screen.queryByText('SPA для двоих')).toBeNull();
    expect(screen.getByText('Введите код купона для поиска')).toBeTruthy();
  });

  it('shows a not-found state only for a 404 response', async () => {
    vi.mocked(lookupPurchasedCoupon).mockRejectedValue({
      response: { status: 404, data: { message: "Купон с кодом 'CP-NOTFOUND' не найден" } },
    });
    const user = userEvent.setup();
    render(<PurchasedCouponLookupPage />);

    await user.type(screen.getByPlaceholderText('Введите код купона (CP-XXXX1234)'), 'CP-NOTFOUND');
    await user.click(screen.getByRole('button', { name: /Найти/ }));

    expect(await screen.findByText('Купон не найден')).toBeTruthy();
    expect(screen.getByText("Купон с кодом 'CP-NOTFOUND' не найден")).toBeTruthy();
  });

  it('does not misreport a service failure as a missing coupon', async () => {
    vi.mocked(lookupPurchasedCoupon).mockRejectedValue({
      response: { status: 503, data: { message: 'order-service недоступен' } },
    });
    const user = userEvent.setup();
    render(<PurchasedCouponLookupPage />);

    await user.type(screen.getByPlaceholderText('Введите код купона (CP-XXXX1234)'), 'CP-ABCD1234');
    await user.click(screen.getByRole('button', { name: /Найти/ }));

    expect(await screen.findByText('Ошибка поиска купона')).toBeTruthy();
    expect(screen.getByText('order-service недоступен')).toBeTruthy();
    expect(screen.queryByText('Купон не найден')).toBeNull();
  });

  it('does not call the API for an empty code', async () => {
    const user = userEvent.setup();
    render(<PurchasedCouponLookupPage />);

    await user.click(screen.getByRole('button', { name: /Найти/ }));

    expect(lookupPurchasedCoupon).not.toHaveBeenCalled();
    expect(screen.getByText('Введите код купона для поиска')).toBeTruthy();
  });
});
