// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../api/auth';
import { ordersApi } from '../api/orders';
import CheckoutDesktop from './CheckoutDesktop';

const { authState, clearCart, navigate, refreshProfile } = vi.hoisted(() => ({
  authState: {
    isAuthenticated: true,
    user: {
      id: 1,
      email: 'a@example.com',
      phone: '+998901234567',
      firstName: 'Ada',
      role: 'USER',
    } as Record<string, unknown>,
  },
  clearCart: vi.fn(),
  navigate: vi.fn(),
  refreshProfile: vi.fn(),
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
  useAuthStore: () => ({
    isAuthenticated: authState.isAuthenticated,
    user: authState.user,
    refreshProfile,
  }),
}));

vi.mock('../store/cartStore', () => ({
  useCartStore: () => ({
    items: [{
      key: '11-111',
      couponOfferId: 11,
      couponOptionId: 111,
      couponTitle: 'Cart item',
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

vi.mock('../api/auth', () => ({
  authApi: {
    requestPhoneOtp: vi.fn(),
    linkPhone: vi.fn(),
  },
}));

// react-imask processes real DOM 'input' events internally to apply the mask;
// jsdom + fireEvent.change doesn't drive that. Stub it with a plain input so
// onAccept fires with the value the test types — same приём, что в
// ProfileSettingsSection.test.tsx / LoginCard.test.tsx.
vi.mock('react-imask', () => ({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  IMaskInput: (props: any) => {
    const { mask, onAccept, inputRef, value, ...rest } = props;
    void mask;
    return (
      <input
        ref={inputRef}
        value={value ?? ''}
        onChange={(e) => onAccept?.(e.target.value)}
        {...rest}
      />
    );
  },
}));

describe('CheckoutDesktop contact gate (T6: phone required, email optional)', () => {
  beforeEach(() => {
    authState.isAuthenticated = true;
    authState.user = {
      id: 1,
      email: 'a@example.com',
      phone: '+998901234567',
      firstName: 'Ada',
      role: 'USER',
    };
    vi.mocked(ordersApi.createOrder).mockReset();
    vi.mocked(authApi.requestPhoneOtp).mockReset();
    vi.mocked(authApi.linkPhone).mockReset();
    clearCart.mockReset();
    navigate.mockReset();
    refreshProfile.mockReset();
    refreshProfile.mockResolvedValue(undefined);
  });

  afterEach(cleanup);

  it('allows payment with a verified phone and a synthetic placeholder email, sending an empty email to the backend', async () => {
    authState.user = {
      id: 1,
      email: 'phone_998901234567@topdim.uz',
      emailPlaceholder: true,
      phone: '+998901234567',
      firstName: 'Ada',
      role: 'USER',
    };
    vi.mocked(ordersApi.createOrder).mockResolvedValue({
      data: { success: true, data: { id: 55 }, timestamp: '2026-08-06T10:00:00Z' },
    } as Awaited<ReturnType<typeof ordersApi.createOrder>>);

    render(<CheckoutDesktop />);

    // Стены «заполните профиль» нет — телефон привязан, email не требуется.
    expect(screen.queryByText('checkout.addPhoneDesc')).toBeNull();
    expect(screen.queryByText('checkout.fillProfile')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'checkout.pay' }));

    await waitFor(() =>
      expect(ordersApi.createOrder).toHaveBeenCalledWith('', '+998901234567'),
    );
  });

  it('shows an inline phone step and links the phone via OTP when the user has no phone', async () => {
    authState.user = {
      id: 2,
      email: 'user@example.com',
      phone: undefined,
      firstName: 'Ada',
      role: 'USER',
    };
    vi.mocked(authApi.requestPhoneOtp).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestPhoneOtp>>,
    );
    vi.mocked(authApi.linkPhone).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.linkPhone>>,
    );

    render(<CheckoutDesktop />);

    expect(screen.getByText('checkout.addPhoneDesc')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'checkout.pay' })).toBeNull();
    expect(ordersApi.createOrder).not.toHaveBeenCalled();

    fireEvent.change(screen.getByLabelText('checkout.addPhoneLabel'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'checkout.addPhoneSendCode' }));

    await waitFor(() =>
      expect(authApi.requestPhoneOtp).toHaveBeenCalledWith('+998901234567'),
    );

    fireEvent.change(await screen.findByLabelText('checkout.addPhoneCodeLabel'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'checkout.addPhoneConfirm' }));

    await waitFor(() =>
      expect(authApi.linkPhone).toHaveBeenCalledWith({ phone: '+998901234567', code: '123456' }),
    );
    expect(refreshProfile).toHaveBeenCalledOnce();
  });

  it('does not call requestPhoneOtp for an invalid phone format', () => {
    authState.user = {
      id: 3,
      email: 'user@example.com',
      phone: undefined,
      firstName: 'Ada',
      role: 'USER',
    };
    render(<CheckoutDesktop />);

    fireEvent.change(screen.getByLabelText('checkout.addPhoneLabel'), {
      target: { value: '123' },
    });

    expect(screen.getByRole('button', { name: 'checkout.addPhoneSendCode' }).getAttribute('disabled')).not.toBeNull();
    expect(authApi.requestPhoneOtp).not.toHaveBeenCalled();
  });

  it('shows a clear message when the phone is already taken by another account (409)', async () => {
    authState.user = {
      id: 4,
      email: 'user@example.com',
      phone: undefined,
      firstName: 'Ada',
      role: 'USER',
    };
    vi.mocked(authApi.requestPhoneOtp).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestPhoneOtp>>,
    );
    vi.mocked(authApi.linkPhone).mockRejectedValue({ response: { status: 409 } });

    render(<CheckoutDesktop />);

    fireEvent.change(screen.getByLabelText('checkout.addPhoneLabel'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'checkout.addPhoneSendCode' }));
    await waitFor(() => expect(authApi.requestPhoneOtp).toHaveBeenCalledOnce());

    fireEvent.change(await screen.findByLabelText('checkout.addPhoneCodeLabel'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'checkout.addPhoneConfirm' }));

    expect(await screen.findByText('checkout.addPhoneTaken')).toBeTruthy();
    expect(refreshProfile).not.toHaveBeenCalled();
  });

  it('shows a clear message for an invalid or expired OTP code (401)', async () => {
    authState.user = {
      id: 5,
      email: 'user@example.com',
      phone: undefined,
      firstName: 'Ada',
      role: 'USER',
    };
    vi.mocked(authApi.requestPhoneOtp).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestPhoneOtp>>,
    );
    vi.mocked(authApi.linkPhone).mockRejectedValue({ response: { status: 401 } });

    render(<CheckoutDesktop />);

    fireEvent.change(screen.getByLabelText('checkout.addPhoneLabel'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'checkout.addPhoneSendCode' }));
    await waitFor(() => expect(authApi.requestPhoneOtp).toHaveBeenCalledOnce());

    fireEvent.change(await screen.findByLabelText('checkout.addPhoneCodeLabel'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'checkout.addPhoneConfirm' }));

    expect(await screen.findByText('checkout.addPhoneInvalidCode')).toBeTruthy();
    expect(refreshProfile).not.toHaveBeenCalled();
  });

  it('does not block on a missing email when the phone is already verified', () => {
    authState.user = {
      id: 6,
      email: '',
      phone: '+998901234567',
      firstName: 'Ada',
      role: 'USER',
    };
    render(<CheckoutDesktop />);

    expect(screen.getByRole('button', { name: 'checkout.pay' })).toBeTruthy();
    expect(screen.queryByText('checkout.addPhoneDesc')).toBeNull();
  });
});
