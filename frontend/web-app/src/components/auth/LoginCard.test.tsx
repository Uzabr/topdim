// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginCard from './LoginCard';

const {
  login,
  telegramLogin,
  googleLogin,
  phoneLogin,
  registerUser,
  requestPhoneOtp,
} = vi.hoisted(() => ({
  login: vi.fn(),
  telegramLogin: vi.fn(),
  googleLogin: vi.fn(),
  phoneLogin: vi.fn(),
  registerUser: vi.fn(),
  requestPhoneOtp: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('../../store/authStore', () => ({
  useAuthStore: () => ({
    login,
    telegramLogin,
    googleLogin,
    phoneLogin,
    register: registerUser,
    isLoading: false,
  }),
}));

vi.mock('../../api/auth', async () => {
  const actual = await vi.importActual<typeof import('../../api/auth')>('../../api/auth');
  return {
    ...actual,
    authApi: {
      ...actual.authApi,
      requestPhoneOtp,
    },
  };
});

vi.mock('./TelegramLoginButton', () => ({
  default: () => null,
}));

// VITE_GOOGLE_CLIENT_ID не задан в этом наборе тестов (см. LoginCard.google.test.tsx
// для сконфигурированной ветки), поэтому реального GoogleLoginButton здесь не будет —
// мок оставлен как страховка на случай, если окружение сборки его выставит.
vi.mock('./GoogleLoginButton', () => ({
  default: () => null,
}));

// react-imask processes real DOM 'input' events internally to apply the mask;
// jsdom + fireEvent.change doesn't drive that. Stub it with a plain input so
// onAccept fires with the value the test types, same contract as the real one.
vi.mock('react-imask', () => ({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  IMaskInput: (props: any) => {
    const { mask, onAccept, inputRef, value, ...rest } = props;
    void mask; // masking is not applied by this stub — only the onAccept contract
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

describe('LoginCard authentication transitions', () => {
  beforeEach(() => {
    login.mockResolvedValue(false);
    telegramLogin.mockResolvedValue(false);
    googleLogin.mockResolvedValue(false);
    phoneLogin.mockResolvedValue(false);
    registerUser.mockResolvedValue(false);
    requestPhoneOtp.mockReset();
    requestPhoneOtp.mockResolvedValue(undefined);
  });

  afterEach(cleanup);

  it('does not report success when a superseded login resolves as cancelled', async () => {
    const onSuccess = vi.fn();
    render(<LoginCard onSuccess={onSuccess} />);

    fireEvent.click(screen.getByRole('button', { name: 'login.viaEmail' }));
    fireEvent.change(screen.getByPlaceholderText('login.email'), {
      target: { value: 'a@example.com' },
    });
    fireEvent.change(screen.getByPlaceholderText('login.password'), {
      target: { value: 'Strong1!' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'login.submitLogin' }));

    await waitFor(() => expect(login).toHaveBeenCalledOnce());
    expect(onSuccess).not.toHaveBeenCalled();
    expect(screen.queryByText('login.serverError')).toBeNull();
  });
});

describe('LoginCard initialMode', () => {
  beforeEach(() => {
    login.mockResolvedValue(false);
    telegramLogin.mockResolvedValue(false);
    googleLogin.mockResolvedValue(false);
    phoneLogin.mockResolvedValue(false);
    registerUser.mockResolvedValue(false);
  });

  afterEach(cleanup);

  it('opens directly on the registration form when initialMode is register (T9 /register deep link)', () => {
    render(<LoginCard onSuccess={vi.fn()} initialMode="register" />);

    expect(screen.getByPlaceholderText('login.firstName')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'login.submitRegister' })).toBeTruthy();
  });

  it('still defaults to the one-tap screen when initialMode is omitted', () => {
    render(<LoginCard onSuccess={vi.fn()} />);

    expect(screen.queryByPlaceholderText('login.firstName')).toBeNull();
    expect(screen.getByRole('button', { name: 'login.viaEmail' })).toBeTruthy();
  });
});

describe('LoginCard phone-OTP flow', () => {
  beforeEach(() => {
    login.mockResolvedValue(false);
    telegramLogin.mockResolvedValue(false);
    googleLogin.mockResolvedValue(false);
    phoneLogin.mockResolvedValue(false);
    registerUser.mockResolvedValue(false);
    requestPhoneOtp.mockReset();
    requestPhoneOtp.mockResolvedValue(undefined);
  });

  afterEach(cleanup);

  it('requests an OTP for the entered phone and moves to the code screen', async () => {
    const onSuccess = vi.fn();
    render(<LoginCard onSuccess={onSuccess} />);

    fireEvent.click(screen.getByRole('button', { name: 'login.viaPhone' }));

    const phoneInput = screen.getByPlaceholderText('login.phoneNumberLabel');
    fireEvent.change(phoneInput, { target: { value: '+998901234567' } });

    fireEvent.click(screen.getByRole('button', { name: 'login.phoneSend' }));

    await waitFor(() => expect(requestPhoneOtp).toHaveBeenCalledWith('+998901234567'));
    expect(await screen.findByPlaceholderText('login.phoneCodeLabel')).toBeTruthy();
    expect(screen.getByText('login.phoneCodeSent')).toBeTruthy();
  });

  it('confirms the code, logs in and calls onSuccess', async () => {
    phoneLogin.mockResolvedValue(true);
    const onSuccess = vi.fn();
    render(<LoginCard onSuccess={onSuccess} />);

    fireEvent.click(screen.getByRole('button', { name: 'login.viaPhone' }));
    fireEvent.change(screen.getByPlaceholderText('login.phoneNumberLabel'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'login.phoneSend' }));
    await waitFor(() => expect(requestPhoneOtp).toHaveBeenCalledOnce());

    fireEvent.change(await screen.findByPlaceholderText('login.phoneCodeLabel'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'login.submitLogin' }));

    await waitFor(() => expect(phoneLogin).toHaveBeenCalledWith('+998901234567', '123456'));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
  });

  it('shows an error message when confirming an invalid/expired code', async () => {
    phoneLogin.mockRejectedValue(new Error('invalid code'));
    const onSuccess = vi.fn();
    render(<LoginCard onSuccess={onSuccess} />);

    fireEvent.click(screen.getByRole('button', { name: 'login.viaPhone' }));
    fireEvent.change(screen.getByPlaceholderText('login.phoneNumberLabel'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'login.phoneSend' }));
    await waitFor(() => expect(requestPhoneOtp).toHaveBeenCalledOnce());

    fireEvent.change(await screen.findByPlaceholderText('login.phoneCodeLabel'), {
      target: { value: '000000' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'login.submitLogin' }));

    await waitFor(() => expect(phoneLogin).toHaveBeenCalledOnce());
    expect(await screen.findByText('login.codeInvalid')).toBeTruthy();
    expect(onSuccess).not.toHaveBeenCalled();
  });
});
