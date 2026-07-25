// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ordersApi } from '../api/orders';
import { advanceSessionGeneration } from '../sessionCleanup';
import CheckoutDesktop from './CheckoutDesktop';
import CheckoutMobile from './CheckoutMobile';

const { authState, clearCart, navigate } = vi.hoisted(() => ({
  authState: {
    isAuthenticated: true,
    user: {
      id: 1,
      email: 'a@example.com',
      phone: '+998901234567',
      firstName: 'Account A',
      role: 'USER',
    },
  },
  clearCart: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  initReactI18next: {
    type: '3rdParty',
    init: vi.fn(),
  },
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: () => authState,
}));

vi.mock('../store/cartStore', () => ({
  useCartStore: () => ({
    items: [{
      key: '11-111',
      couponOfferId: 11,
      couponOptionId: 111,
      couponTitle: 'Account A cart item',
      optionTitle: 'Option',
      unitPrice: 10000,
      quantity: 1,
      addedAt: 1,
    }],
    totalItems: 1,
    totalPrice: 10000,
    clearCart,
  }),
}));

vi.mock('../api/orders', () => ({
  ordersApi: {
    createOrder: vi.fn(),
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

const variants = [
  {
    name: 'desktop',
    Component: CheckoutDesktop,
    submitName: 'checkout.pay',
  },
  {
    name: 'mobile',
    Component: CheckoutMobile,
    submitName: 'checkout.payCta',
  },
] as const;

describe.each(variants)('$name checkout session isolation', ({
  Component,
  submitName,
}) => {
  beforeEach(() => {
    authState.isAuthenticated = true;
    authState.user = {
      id: 1,
      email: 'a@example.com',
      phone: '+998901234567',
      firstName: 'Account A',
      role: 'USER',
    };
    vi.mocked(ordersApi.createOrder).mockReset();
    clearCart.mockReset();
    navigate.mockReset();
  });

  afterEach(cleanup);

  it('does not clear account B cart or navigate for account A late order', async () => {
    const lateOrder =
      deferred<Awaited<ReturnType<typeof ordersApi.createOrder>>>();
    vi.mocked(ordersApi.createOrder).mockReturnValue(lateOrder.promise);
    render(<Component />);

    fireEvent.click(screen.getByRole('button', { name: submitName }));
    await waitFor(() => expect(ordersApi.createOrder).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      lateOrder.resolve({
        data: {
          success: true,
          data: { id: 101 },
          timestamp: '2026-07-25T10:00:00Z',
        },
      } as Awaited<ReturnType<typeof ordersApi.createOrder>>);
    });

    expect(clearCart).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not show account A late order error to account B', async () => {
    const lateOrder =
      deferred<Awaited<ReturnType<typeof ordersApi.createOrder>>>();
    vi.mocked(ordersApi.createOrder).mockReturnValue(lateOrder.promise);
    render(<Component />);

    fireEvent.click(screen.getByRole('button', { name: submitName }));
    await waitFor(() => expect(ordersApi.createOrder).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      lateOrder.reject({
        response: { data: { message: 'Account A checkout error' } },
      });
    });

    expect(screen.queryByText('Account A checkout error')).toBeNull();
    expect(clearCart).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('re-enables checkout for account B while account A order remains pending', async () => {
    const lateOrder =
      deferred<Awaited<ReturnType<typeof ordersApi.createOrder>>>();
    vi.mocked(ordersApi.createOrder).mockReturnValue(lateOrder.promise);
    const view = render(<Component />);

    fireEvent.click(screen.getByRole('button', { name: submitName }));
    await waitFor(() => expect(ordersApi.createOrder).toHaveBeenCalledOnce());
    expect(screen.getByRole('button', {
      name: 'checkout.processing',
    }).getAttribute('disabled')).not.toBeNull();

    advanceSessionGeneration();
    authState.user = {
      id: 2,
      email: 'b@example.com',
      phone: '+998907654321',
      firstName: 'Account B',
      role: 'USER',
    };
    view.rerender(<Component />);

    expect(screen.getByRole('button', {
      name: submitName,
    }).getAttribute('disabled')).toBeNull();
    expect(screen.queryByText('Account A checkout error')).toBeNull();
  });
});
