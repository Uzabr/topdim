// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ordersApi } from '../api/orders';
import { paymentsApi, type PaymentResponse } from '../api/payments';
import PaymentDesktop from './PaymentDesktop';

/**
 * T8: на экране успеха покупки — призыв «Получать купоны на почту? Добавить»,
 * только пока у юзера синтетический (placeholder) email.
 */

const { authState, navigate, translate } = vi.hoisted(() => ({
  authState: { user: { id: 1, emailPlaceholder: false } as { id: number; emailPlaceholder?: boolean } },
  navigate: vi.fn(),
  translate: (key: string) => key,
}));

vi.mock('react-i18next', () => ({
  initReactI18next: {
    type: '3rdParty',
    init: vi.fn(),
  },
  useTranslation: () => ({
    t: translate,
  }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
  useParams: () => ({ orderId: '77' }),
}));

vi.mock('@tanstack/react-query', async (importOriginal) => ({
  ...await importOriginal<typeof import('@tanstack/react-query')>(),
  useQueryClient: () => ({ invalidateQueries: vi.fn() }),
}));

vi.mock('../hooks/useLocalePath', () => ({
  useLocalePath: () => (path: string) => `/ru${path}`,
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: (
    selector: (state: { user: { id: number; emailPlaceholder?: boolean } }) => unknown,
  ) => selector(authState),
}));

vi.mock('../api/orders', () => ({
  ordersApi: {
    getOrder: vi.fn(),
  },
}));

vi.mock('../api/payments', () => ({
  paymentsApi: {
    getByOrderId: vi.fn(),
    demoComplete: vi.fn(),
  },
}));

function paymentResponse(payment: PaymentResponse) {
  return {
    data: {
      success: true,
      data: payment,
      timestamp: '2026-08-06T10:00:00Z',
    },
  } as Awaited<ReturnType<typeof paymentsApi.getByOrderId>>;
}

function orderResponse(totalAmount = 91000) {
  return {
    data: {
      success: true,
      data: {
        id: 77,
        orderNumber: 'ORD-A',
        totalAmount,
        status: 'PENDING',
        userEmail: 'a@example.com',
        userPhone: '+998901234567',
        itemCount: 1,
        createdAt: '2026-08-06T10:00:00Z',
      },
      timestamp: '2026-08-06T10:00:00Z',
    },
  } as Awaited<ReturnType<typeof ordersApi.getOrder>>;
}

const completedPayment: PaymentResponse = {
  id: 701,
  orderId: 77,
  userId: 1,
  amount: 91000,
  currency: 'UZS',
  provider: 'DEMO',
  statusName: 'COMPLETED',
  paymentMode: 'demo',
  createdAt: '2026-08-06T10:00:00Z',
  transactionId: 'tx-1',
  completedAt: '2026-08-06T10:01:00Z',
};

describe('PaymentDesktop — email delivery channel prompt (T8)', () => {
  beforeEach(() => {
    authState.user = { id: 1, emailPlaceholder: false };
    navigate.mockReset();
    vi.mocked(ordersApi.getOrder).mockReset();
    vi.mocked(paymentsApi.getByOrderId).mockReset();
    vi.mocked(paymentsApi.demoComplete).mockReset();
    vi.mocked(ordersApi.getOrder).mockResolvedValue(orderResponse());
    vi.mocked(paymentsApi.getByOrderId).mockResolvedValue(paymentResponse(completedPayment));
  });

  afterEach(() => {
    cleanup();
  });

  it('shows the "add email" prompt when completed and the user only has a placeholder email, and navigates to profile settings on click', async () => {
    authState.user = { id: 1, emailPlaceholder: true };
    render(<PaymentDesktop />);

    await screen.findByText('payment.successTitle');

    const prompt = screen.getByRole('button', { name: 'payment.getCouponsByEmail' });
    fireEvent.click(prompt);
    expect(navigate).toHaveBeenCalledWith('/ru/profile?tab=settings');
  });

  it('does not show the prompt when the user already has a real email', async () => {
    authState.user = { id: 1, emailPlaceholder: false };
    render(<PaymentDesktop />);

    await screen.findByText('payment.successTitle');

    expect(screen.queryByText('payment.getCouponsByEmail')).toBeNull();
  });
});
