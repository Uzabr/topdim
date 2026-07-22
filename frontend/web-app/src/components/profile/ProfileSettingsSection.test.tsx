// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../../api/auth';
import ProfileSettingsSection from './ProfileSettingsSection';

const { navigate, logout, updateProfile } = vi.hoisted(() => ({
  navigate: vi.fn(),
  logout: vi.fn(),
  updateProfile: vi.fn(),
}));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru', changeLanguage: vi.fn() },
  }),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => navigate,
  useLocation: () => ({ pathname: '/ru/profile', search: '' }),
}));

vi.mock('../../store/authStore', () => ({
  useAuthStore: () => ({
    user: { id: 1, email: 'user@example.com', firstName: 'Ada', role: 'USER' },
    updateProfile,
    logout,
  }),
}));

vi.mock('../../api/auth', () => ({
  authApi: { changePassword: vi.fn() },
}));

vi.mock('./NotificationsSection', () => ({ default: () => null }));

function openPasswordForm() {
  render(<ProfileSettingsSection />);
  fireEvent.click(screen.getByRole('button', { name: 'profile.settings.password.change' }));
}

function fillPasswords(current: string, next: string, confirmation: string) {
  fireEvent.change(screen.getByLabelText('profile.settings.password.current'), {
    target: { value: current },
  });
  fireEvent.change(screen.getByLabelText('profile.settings.password.new'), {
    target: { value: next },
  });
  fireEvent.change(screen.getByLabelText('profile.settings.password.confirm'), {
    target: { value: confirmation },
  });
}

describe('ProfileSettingsSection password change', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(authApi.changePassword).mockReset();
  });

  it('does not call backend when password rules or confirmation fail', () => {
    openPasswordForm();
    fillPasswords('Current1!', 'weak', 'different');

    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.password.save' }));

    expect(screen.getByText('profile.settings.password.weak')).toBeTruthy();
    expect(screen.getByText('profile.settings.password.mismatch')).toBeTruthy();
    expect(authApi.changePassword).not.toHaveBeenCalled();
  });

  it('shows a current-password field error for backend 401', async () => {
    vi.mocked(authApi.changePassword).mockRejectedValue({
      response: { status: 401, data: { message: 'Неверный текущий пароль' } },
    });
    openPasswordForm();
    fillPasswords('Wrong1!', 'NewStrong2!', 'NewStrong2!');

    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.password.save' }));

    expect(await screen.findByText('Неверный текущий пароль')).toBeTruthy();
  });

  it('submits backend contract and shows re-login notice on success', async () => {
    vi.mocked(authApi.changePassword).mockResolvedValue({} as Awaited<ReturnType<typeof authApi.changePassword>>);
    openPasswordForm();
    fillPasswords('Current1!', 'NewStrong2!', 'NewStrong2!');

    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.password.save' }));

    await waitFor(() => expect(authApi.changePassword).toHaveBeenCalledWith('Current1!', 'NewStrong2!'));
    expect((await screen.findByRole('status')).textContent).toBe(
      'profile.settings.password.success',
    );
  });
});
