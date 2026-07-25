// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LoginCard from './LoginCard';

const {
  login,
  telegramLogin,
  registerUser,
} = vi.hoisted(() => ({
  login: vi.fn(),
  telegramLogin: vi.fn(),
  registerUser: vi.fn(),
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
    register: registerUser,
    isLoading: false,
  }),
}));

vi.mock('./TelegramLoginButton', () => ({
  default: () => null,
}));

describe('LoginCard authentication transitions', () => {
  beforeEach(() => {
    login.mockResolvedValue(false);
    telegramLogin.mockResolvedValue(false);
    registerUser.mockResolvedValue(false);
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
