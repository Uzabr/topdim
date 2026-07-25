// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../../api/auth';
import { mediaApi } from '../../api/media';
import ru from '../../locales/ru.json';
import uz from '../../locales/uz.json';
import { advanceSessionGeneration } from '../../sessionCleanup';
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
  authApi: { changePassword: vi.fn(), requestEmailConfirm: vi.fn() },
}));

vi.mock('../../api/media', () => ({
  mediaApi: { uploadFile: vi.fn() },
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

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((complete, fail) => {
    resolve = complete;
    reject = fail;
  });
  return { promise, resolve, reject };
}

describe('ProfileSettingsSection profile actions', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(authApi.changePassword).mockReset();
    vi.mocked(authApi.requestEmailConfirm).mockReset();
    vi.mocked(mediaApi.uploadFile).mockReset();
    updateProfile.mockReset();
    logout.mockReset();
    navigate.mockReset();
  });

  it('requests email confirmation and shows sent notice', async () => {
    vi.mocked(authApi.requestEmailConfirm).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestEmailConfirm>>,
    );
    render(<ProfileSettingsSection />);

    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.email.confirm' }));

    await waitFor(() => expect(authApi.requestEmailConfirm).toHaveBeenCalledOnce());
    expect((await screen.findByRole('status')).textContent).toBe(
      'profile.settings.email.sent',
    );
  });

  it('rejects non-image avatar before upload', () => {
    render(<ProfileSettingsSection />);
    const input = screen.getByLabelText('profile.settings.avatar.choose');

    fireEvent.change(input, {
      target: { files: [new File(['text'], 'avatar.png', { type: 'text/plain' })] },
    });

    expect(screen.getByText('profile.settings.avatar.typeError')).toBeTruthy();
    expect(mediaApi.uploadFile).not.toHaveBeenCalled();
  });

  it('uploads avatar and persists returned URL in profile', async () => {
    const file = new File(['image'], 'avatar.png', { type: 'image/png' });
    vi.mocked(mediaApi.uploadFile).mockResolvedValue({
      fileName: 'generated_avatar.png',
      url: '/api/v1/media/generated_avatar.png',
    });
    updateProfile.mockResolvedValue(undefined);
    render(<ProfileSettingsSection />);

    fireEvent.change(screen.getByLabelText('profile.settings.avatar.choose'), {
      target: { files: [file] },
    });

    await waitFor(() => expect(mediaApi.uploadFile).toHaveBeenCalledWith(file));
    expect(updateProfile).toHaveBeenCalledWith({
      avatarUrl: '/api/v1/media/generated_avatar.png',
    });
    expect((await screen.findByRole('status')).textContent).toBe(
      'profile.settings.avatar.success',
    );
  });

  it('does not apply an account A avatar upload after the session switches', async () => {
    const lateUpload = deferred<Awaited<ReturnType<typeof mediaApi.uploadFile>>>();
    const file = new File(['image'], 'avatar.png', { type: 'image/png' });
    vi.mocked(mediaApi.uploadFile).mockReturnValue(lateUpload.promise);
    render(<ProfileSettingsSection />);

    fireEvent.change(screen.getByLabelText('profile.settings.avatar.choose'), {
      target: { files: [file] },
    });
    await waitFor(() => expect(mediaApi.uploadFile).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      lateUpload.resolve({
        fileName: 'account_a_avatar.png',
        url: '/api/v1/media/account_a_avatar.png',
      });
    });

    expect(updateProfile).not.toHaveBeenCalled();
    expect(screen.queryByText('profile.settings.avatar.success')).toBeNull();
  });

  it('does not submit a phone outside the canonical Uzbekistan format', () => {
    render(<ProfileSettingsSection />);

    fireEvent.click(screen.getByRole('button', { name: 'common.add' }));
    fireEvent.change(screen.getByPlaceholderText('+998 90 123 45 67'), {
      target: { value: '+998abcdefgh' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'common.save' }));

    expect(screen.getByText('profile.settings.validation.phoneMin')).toBeTruthy();
    expect(updateProfile).not.toHaveBeenCalled();
  });

  it('submits an empty trimmed last name so the backend can clear it', async () => {
    updateProfile.mockResolvedValue(undefined);
    render(<ProfileSettingsSection />);

    fireEvent.click(screen.getByRole('button', { name: 'common.edit' }));
    fireEvent.change(screen.getByPlaceholderText('profile.settings.lastNamePlaceholder'), {
      target: { value: '   ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'common.save' }));

    await waitFor(() => expect(updateProfile).toHaveBeenCalledWith({
      firstName: 'Ada',
      lastName: '',
    }));
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
    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
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

  it('logs out and navigates immediately after password change even if unmounted', async () => {
    let resolveChangePassword!: (
      value: Awaited<ReturnType<typeof authApi.changePassword>>,
    ) => void;
    vi.mocked(authApi.changePassword).mockImplementation(
      () => new Promise((resolve) => {
        resolveChangePassword = resolve;
      }),
    );
    const view = render(<ProfileSettingsSection />);
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.password.change' }));
    fillPasswords('Current1!', 'NewStrong2!', 'NewStrong2!');
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.password.save' }));

    await waitFor(() => expect(authApi.changePassword).toHaveBeenCalledOnce());
    view.unmount();
    await act(async () => {
      resolveChangePassword(
        {} as Awaited<ReturnType<typeof authApi.changePassword>>,
      );
    });

    expect(logout).toHaveBeenCalledOnce();
    expect(navigate).toHaveBeenCalledWith('/ru/login', { replace: true });
  });

  it('does not log out account B when account A password change resolves late', async () => {
    const latePasswordChange =
      deferred<Awaited<ReturnType<typeof authApi.changePassword>>>();
    vi.mocked(authApi.changePassword).mockReturnValue(
      latePasswordChange.promise,
    );
    openPasswordForm();
    fillPasswords('Current1!', 'NewStrong2!', 'NewStrong2!');
    fireEvent.click(screen.getByRole('button', {
      name: 'profile.settings.password.save',
    }));
    await waitFor(() => expect(authApi.changePassword).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      latePasswordChange.resolve(
        {} as Awaited<ReturnType<typeof authApi.changePassword>>,
      );
    });

    expect(logout).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(screen.queryByText('profile.settings.password.success')).toBeNull();
  });

  it('does not surface an account A password error after the session switches', async () => {
    const latePasswordChange =
      deferred<Awaited<ReturnType<typeof authApi.changePassword>>>();
    vi.mocked(authApi.changePassword).mockReturnValue(
      latePasswordChange.promise,
    );
    openPasswordForm();
    fillPasswords('Current1!', 'NewStrong2!', 'NewStrong2!');
    fireEvent.click(screen.getByRole('button', {
      name: 'profile.settings.password.save',
    }));
    await waitFor(() => expect(authApi.changePassword).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      latePasswordChange.reject({
        response: { status: 401, data: { message: 'Account A error' } },
      });
    });

    expect(screen.queryByText('Account A error')).toBeNull();
    expect(logout).not.toHaveBeenCalled();
  });

  it('labels the notifications action as viewing rather than configuring', () => {
    expect(ru.profile.settings.view).toBe('Смотреть');
    expect(uz.profile.settings.view).toBe("Ko'rish");
    expect('configure' in ru.profile.settings).toBe(false);
    expect('configure' in uz.profile.settings).toBe(false);
  });
});
