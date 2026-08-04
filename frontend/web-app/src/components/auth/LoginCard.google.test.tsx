// @vitest-environment jsdom
import type { ComponentType } from 'react';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const { googleLogin } = vi.hoisted(() => ({
  googleLogin: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
  }),
}));

vi.mock('../../store/authStore', () => ({
  useAuthStore: () => ({
    login: vi.fn(),
    telegramLogin: vi.fn(),
    googleLogin,
    phoneLogin: vi.fn(),
    register: vi.fn(),
    isLoading: false,
  }),
}));

vi.mock('./TelegramLoginButton', () => ({
  default: () => null,
}));

// Заменяем реальный GIS-виджет кликабельной заглушкой: сам GoogleLoginButton
// (загрузка скрипта, initialize/renderButton, разбор credential) уже покрыт
// отдельно в GoogleLoginButton.test.tsx. Здесь проверяется только проводка
// onAuth(idToken) → googleLogin → onSuccess внутри LoginCard.
vi.mock('./GoogleLoginButton', () => ({
  default: ({ onAuth }: { onAuth: (idToken: string) => void }) => (
    <button type="button" onClick={() => onAuth('fake-id-token')}>
      mock-google-button
    </button>
  ),
}));

/**
 * VITE_GOOGLE_CLIENT_ID читается ОДИН РАЗ в модульную константу при импорте
 * LoginCard.tsx, поэтому обычный статический import не даст протестировать
 * «сконфигурированную» ветку — переменная окружения должна быть выставлена
 * ДО первого импорта модуля. vi.stubEnv правит и process.env, и
 * import.meta.env; vi.resetModules() форсирует повторное вычисление модуля
 * при следующем динамическом import().
 */
describe('LoginCard Google (GIS) wiring — VITE_GOOGLE_CLIENT_ID configured', () => {
  let LoginCard: ComponentType<{ onSuccess: () => void }>;

  beforeEach(async () => {
    vi.stubEnv('VITE_GOOGLE_CLIENT_ID', 'test-client-id');
    vi.resetModules();
    ({ default: LoginCard } = await import('./LoginCard'));
    googleLogin.mockReset();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllEnvs();
  });

  it('renders the real Google button instead of the "soon" placeholder', async () => {
    render(<LoginCard onSuccess={vi.fn()} />);

    expect(await screen.findByText('mock-google-button')).toBeTruthy();
    expect(screen.queryByText('common.soon')).toBeNull();
  });

  it('logs in via googleLogin and calls onSuccess when GIS returns a credential', async () => {
    googleLogin.mockResolvedValue(true);
    const onSuccess = vi.fn();
    render(<LoginCard onSuccess={onSuccess} />);

    fireEvent.click(await screen.findByText('mock-google-button'));

    await waitFor(() => expect(googleLogin).toHaveBeenCalledWith('fake-id-token'));
    await waitFor(() => expect(onSuccess).toHaveBeenCalledOnce());
  });

  it('shows a server error and does not call onSuccess when googleLogin rejects', async () => {
    googleLogin.mockRejectedValue(new Error('google auth failed'));
    const onSuccess = vi.fn();
    render(<LoginCard onSuccess={onSuccess} />);

    fireEvent.click(await screen.findByText('mock-google-button'));

    await waitFor(() => expect(googleLogin).toHaveBeenCalledOnce());
    expect(await screen.findByText('login.serverError')).toBeTruthy();
    expect(onSuccess).not.toHaveBeenCalled();
  });
});
