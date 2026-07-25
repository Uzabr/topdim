// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ordersApi } from '../api/orders';
import { paymentsApi, type PaymentResponse } from '../api/payments';
import {
  advanceSessionGeneration,
  invalidateClientSession,
} from '../sessionCleanup';
import PaymentDesktop from './PaymentDesktop';
import PaymentMobile from './PaymentMobile';

const { authState, invalidateQueries, navigate, translate } = vi.hoisted(() => ({
  authState: { user: { id: 1 } },
  invalidateQueries: vi.fn(),
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
  useQuery: () => ({ data: null }),
  useQueryClient: () => ({ invalidateQueries }),
}));

vi.mock('qrcode.react', () => ({
  QRCodeSVG: () => <svg aria-label="qr-code" />,
}));

vi.mock('../hooks/useLocalePath', () => ({
  useLocalePath: () => (path: string) => `/ru${path}`,
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: (
    selector: (state: { user: { id: number } }) => unknown,
  ) => selector(authState),
}));

vi.mock('../api/orders', () => ({
  ordersApi: {
    getOrder: vi.fn(),
    getMyCoupons: vi.fn(),
  },
}));

vi.mock('../api/payments', () => ({
  paymentsApi: {
    getByOrderId: vi.fn(),
    demoComplete: vi.fn(),
  },
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { promise, resolve, reject };
}

function paymentResponse(payment: PaymentResponse) {
  return {
    data: {
      success: true,
      data: payment,
      timestamp: '2026-07-25T10:00:00Z',
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
        createdAt: '2026-07-25T10:00:00Z',
      },
      timestamp: '2026-07-25T10:00:00Z',
    },
  } as Awaited<ReturnType<typeof ordersApi.getOrder>>;
}

const pendingPaymentA: PaymentResponse = {
  id: 701,
  orderId: 77,
  userId: 1,
  amount: 91000,
  currency: 'UZS',
  provider: 'DEMO',
  statusName: 'PENDING',
  paymentMode: 'demo',
  createdAt: '2026-07-25T10:00:00Z',
};

const completedPaymentA: PaymentResponse = {
  ...pendingPaymentA,
  statusName: 'COMPLETED',
  transactionId: 'account-a-private-transaction',
  completedAt: '2026-07-25T10:01:00Z',
};

const variants = [
  {
    name: 'desktop',
    Component: PaymentDesktop,
    completedTitle: 'payment.successTitle',
  },
  {
    name: 'mobile',
    Component: PaymentMobile,
    completedTitle: 'mobile.paid.title',
  },
] as const;

describe.each(variants)('$name payment session isolation', ({
  Component,
  completedTitle,
}) => {
  beforeEach(() => {
    authState.user.id = 1;
    navigate.mockReset();
    invalidateQueries.mockReset();
    vi.mocked(ordersApi.getOrder).mockReset();
    vi.mocked(ordersApi.getMyCoupons).mockReset();
    vi.mocked(paymentsApi.getByOrderId).mockReset();
    vi.mocked(paymentsApi.demoComplete).mockReset();
    vi.mocked(ordersApi.getOrder).mockResolvedValue(orderResponse());
    vi.mocked(paymentsApi.getByOrderId).mockResolvedValue(
      paymentResponse(pendingPaymentA),
    );
    vi.mocked(paymentsApi.demoComplete).mockResolvedValue(
      paymentResponse(completedPaymentA),
    );
  });

  afterEach(() => {
    cleanup();
    vi.useRealTimers();
  });

  it('does not apply account A order or payment success after the generation changes', async () => {
    const lateOrder =
      deferred<Awaited<ReturnType<typeof ordersApi.getOrder>>>();
    const latePayment =
      deferred<Awaited<ReturnType<typeof paymentsApi.getByOrderId>>>();
    vi.mocked(ordersApi.getOrder).mockReturnValue(lateOrder.promise);
    vi.mocked(paymentsApi.getByOrderId).mockReturnValue(latePayment.promise);
    render(<Component />);

    await waitFor(() => {
      expect(ordersApi.getOrder).toHaveBeenCalledOnce();
      expect(paymentsApi.getByOrderId).toHaveBeenCalledOnce();
    });

    advanceSessionGeneration();
    await act(async () => {
      lateOrder.resolve(orderResponse());
      latePayment.resolve(paymentResponse(pendingPaymentA));
    });

    expect(screen.queryByText('payment.confirmTitle')).toBeNull();
    expect(screen.getByText('payment.creating')).toBeTruthy();
  });

  it('does not surface account A polling error after the generation changes', async () => {
    const latePayment =
      deferred<Awaited<ReturnType<typeof paymentsApi.getByOrderId>>>();
    vi.mocked(paymentsApi.getByOrderId).mockReturnValue(latePayment.promise);
    render(<Component />);

    await waitFor(() => expect(paymentsApi.getByOrderId).toHaveBeenCalledOnce());

    invalidateClientSession();
    await act(async () => {
      latePayment.reject({
        response: {
          status: 500,
          data: { message: 'Account A private payment error' },
        },
      });
    });

    expect(screen.queryByText('Account A private payment error')).toBeNull();
    expect(screen.getByText('payment.creating')).toBeTruthy();
  });

  it('stops scheduled polling before another account A request is sent', async () => {
    vi.useFakeTimers();
    vi.mocked(paymentsApi.getByOrderId).mockRejectedValue({
      response: { status: 404 },
    });
    render(<Component />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(paymentsApi.getByOrderId).toHaveBeenCalledOnce();

    advanceSessionGeneration();
    await act(async () => {
      vi.advanceTimersByTime(6000);
      await Promise.resolve();
    });

    expect(paymentsApi.getByOrderId).toHaveBeenCalledOnce();
  });

  it('does not let a late account A poll stop account B polling', async () => {
    vi.useFakeTimers();
    const lateAccountAPoll =
      deferred<Awaited<ReturnType<typeof paymentsApi.getByOrderId>>>();
    vi.mocked(paymentsApi.getByOrderId)
      .mockReturnValueOnce(lateAccountAPoll.promise)
      .mockRejectedValue({ response: { status: 404 } });
    const view = render(<Component />);

    await act(async () => {
      await Promise.resolve();
    });
    expect(paymentsApi.getByOrderId).toHaveBeenCalledOnce();

    advanceSessionGeneration();
    authState.user.id = 2;
    view.rerender(<Component />);
    await act(async () => {
      await Promise.resolve();
    });
    expect(paymentsApi.getByOrderId).toHaveBeenCalledTimes(2);

    await act(async () => {
      lateAccountAPoll.resolve(paymentResponse(pendingPaymentA));
    });
    await act(async () => {
      vi.advanceTimersByTime(2000);
      await Promise.resolve();
    });

    expect(paymentsApi.getByOrderId).toHaveBeenCalledTimes(3);
  });

  it('does not apply a late account A demo completion and releases loading', async () => {
    const lateCompletion =
      deferred<Awaited<ReturnType<typeof paymentsApi.demoComplete>>>();
    vi.mocked(paymentsApi.demoComplete).mockReturnValue(lateCompletion.promise);
    render(<Component />);

    const confirm = await screen.findByRole('button', {
      name: 'payment.confirmPurchase',
    });
    fireEvent.click(confirm);
    expect((confirm as HTMLButtonElement).disabled).toBe(true);

    advanceSessionGeneration();
    await act(async () => {
      lateCompletion.resolve(paymentResponse(completedPaymentA));
    });

    expect(screen.queryByText(completedTitle)).toBeNull();
    expect(screen.getByText('payment.confirmTitle')).toBeTruthy();
    expect((screen.getByRole('button', {
      name: 'payment.confirmPurchase',
    }) as HTMLButtonElement).disabled).toBe(false);
    expect(invalidateQueries).not.toHaveBeenCalled();
  });

  it('does not surface a late account A demo error and releases loading', async () => {
    const lateCompletion =
      deferred<Awaited<ReturnType<typeof paymentsApi.demoComplete>>>();
    vi.mocked(paymentsApi.demoComplete).mockReturnValue(lateCompletion.promise);
    render(<Component />);

    const confirm = await screen.findByRole('button', {
      name: 'payment.confirmPurchase',
    });
    fireEvent.click(confirm);
    expect((confirm as HTMLButtonElement).disabled).toBe(true);

    advanceSessionGeneration();
    await act(async () => {
      lateCompletion.reject({
        response: {
          status: 500,
          data: { message: 'Account A private confirmation error' },
        },
      });
    });

    expect(screen.queryByText('Account A private confirmation error')).toBeNull();
    expect((screen.getByRole('button', {
      name: 'payment.confirmPurchase',
    }) as HTMLButtonElement).disabled).toBe(false);
  });
});
