// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import type { LocalCartItem } from '../store/cartStore';
import CartDesktop from './CartDesktop';

const navigateMock = vi.hoisted(() => vi.fn());

const authState = vi.hoisted(() => ({
  isAuthenticated: false,
}));

const guestItem: LocalCartItem = {
  key: '1-11',
  couponOfferId: 1,
  couponOptionId: 11,
  couponTitle: 'Ужин на двоих',
  optionTitle: 'Стандартный набор',
  unitPrice: 100000,
  quantity: 1,
  addedAt: 1,
};

const cartState = vi.hoisted(() => ({
  items: [] as LocalCartItem[],
  totalItems: 0,
  totalPrice: 0,
  removeFromCart: vi.fn(),
  updateQuantity: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('react-router-dom', () => ({
  Link: ({ children, to }: { children: ReactNode; to: string }) => <a href={to}>{children}</a>,
  useNavigate: () => navigateMock,
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: () => authState,
}));

vi.mock('../store/cartStore', () => ({
  useCartStore: () => cartState,
}));

vi.mock('../hooks/useLocalePath', () => ({
  useLocalePath: () => (path: string) => `/ru${path}`,
}));

vi.mock('../utils/format', () => ({
  formatPrice: (value: number) => String(value),
  calcDiscount: () => 0,
}));

describe('CartDesktop guest cart visibility', () => {
  afterEach(cleanup);

  beforeEach(() => {
    navigateMock.mockReset();
    cartState.removeFromCart.mockReset();
    cartState.updateQuantity.mockReset();
    authState.isAuthenticated = false;
    cartState.items = [guestItem];
    cartState.totalItems = 1;
    cartState.totalPrice = 100000;
  });

  it('shows the guest cart items and total instead of the login prompt', () => {
    render(<CartDesktop />);

    expect(screen.getByText('Ужин на двоих')).toBeTruthy();
    expect(screen.queryByText('cart.guestTitle')).toBeNull();
    expect(screen.getByRole('button', { name: 'cart.checkout' })).toBeTruthy();
  });

  it('sends an unauthenticated guest to login with a return path to checkout', () => {
    render(<CartDesktop />);

    fireEvent.click(screen.getByRole('button', { name: 'cart.checkout' }));

    expect(navigateMock).toHaveBeenCalledWith('/ru/login', { state: { from: '/ru/checkout' } });
  });

  it('sends an authenticated user straight to checkout', () => {
    authState.isAuthenticated = true;

    render(<CartDesktop />);
    fireEvent.click(screen.getByRole('button', { name: 'cart.checkout' }));

    expect(navigateMock).toHaveBeenCalledWith('/ru/checkout');
  });

  it('still shows the empty-cart state when the guest cart is empty', () => {
    cartState.items = [];
    cartState.totalItems = 0;
    cartState.totalPrice = 0;

    render(<CartDesktop />);

    expect(screen.getByText('cart.emptyTitleDesktop')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'cart.checkout' })).toBeNull();
  });
});
