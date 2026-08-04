// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { authApi } from '../../api/auth';
import { mediaApi } from '../../api/media';
import ru from '../../locales/ru.json';
import uz from '../../locales/uz.json';
import { advanceSessionGeneration } from '../../sessionCleanup';
import ProfileSettingsSection from './ProfileSettingsSection';

const { navigate, logout, updateProfile, refreshProfile } = vi.hoisted(() => ({
  navigate: vi.fn(),
  logout: vi.fn(),
  updateProfile: vi.fn(),
  refreshProfile: vi.fn(),
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
    refreshProfile,
  }),
}));

vi.mock('../../api/auth', () => ({
  authApi: {
    changePassword: vi.fn(),
    requestEmailConfirm: vi.fn(),
    requestEmailChange: vi.fn(),
    requestPhoneOtp: vi.fn(),
    linkPhone: vi.fn(),
  },
}));

vi.mock('../../api/media', () => ({
  mediaApi: { uploadFile: vi.fn() },
}));

vi.mock('./NotificationsSection', () => ({ default: () => null }));

// react-imask processes real DOM 'input' events internally to apply the mask;
// jsdom + fireEvent.change doesn't drive that. Stub it with a plain input so
// onAccept fires with the value the test types — same contract as the real
// component (см. LoginCard.test.tsx для того же приёма).
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
    vi.mocked(authApi.requestEmailChange).mockReset();
    vi.mocked(authApi.requestPhoneOtp).mockReset();
    vi.mocked(authApi.linkPhone).mockReset();
    vi.mocked(mediaApi.uploadFile).mockReset();
    updateProfile.mockReset();
    logout.mockReset();
    navigate.mockReset();
    refreshProfile.mockReset();
    refreshProfile.mockResolvedValue(undefined);
  });

  function openPhoneLinkForm() {
    render(<ProfileSettingsSection />);
    fireEvent.click(screen.getByRole('button', { name: 'common.add' }));
  }

  function openEmailChangeForm() {
    render(<ProfileSettingsSection />);
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.email.changeAction' }));
  }

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

  it('requests an email change for a valid new address and shows the confirmation notice', async () => {
    vi.mocked(authApi.requestEmailChange).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestEmailChange>>,
    );
    openEmailChangeForm();

    fireEvent.change(screen.getByLabelText('profile.settings.email.changeLabel'), {
      target: { value: 'new@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.email.changeSubmit' }));

    await waitFor(() =>
      expect(authApi.requestEmailChange).toHaveBeenCalledWith('new@example.com'),
    );
    expect((await screen.findByRole('status')).textContent).toBe(
      'profile.settings.email.changeSent',
    );
    // Форма закрывается после успешной отправки.
    expect(screen.queryByLabelText('profile.settings.email.changeLabel')).toBeNull();
  });

  it('rejects an invalid new email before calling the backend', () => {
    openEmailChangeForm();

    fireEvent.change(screen.getByLabelText('profile.settings.email.changeLabel'), {
      target: { value: 'not-an-email' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.email.changeSubmit' }));

    expect(screen.getByText('profile.settings.email.changeInvalid')).toBeTruthy();
    expect(authApi.requestEmailChange).not.toHaveBeenCalled();
  });

  it('shows a clear message when the new email is already taken (409)', async () => {
    vi.mocked(authApi.requestEmailChange).mockRejectedValue({
      response: { status: 409 },
    });
    openEmailChangeForm();

    fireEvent.change(screen.getByLabelText('profile.settings.email.changeLabel'), {
      target: { value: 'taken@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.email.changeSubmit' }));

    expect(await screen.findByText('profile.settings.email.changeTaken')).toBeTruthy();
  });

  it('cancels the email change form without calling the backend', () => {
    openEmailChangeForm();

    fireEvent.change(screen.getByLabelText('profile.settings.email.changeLabel'), {
      target: { value: 'new@example.com' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'common.cancel' }));

    expect(screen.queryByLabelText('profile.settings.email.changeLabel')).toBeNull();
    expect(authApi.requestEmailChange).not.toHaveBeenCalled();
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

  it('keeps the send-code button disabled for a phone outside the canonical Uzbekistan format', () => {
    openPhoneLinkForm();

    const sendButton = screen.getByRole('button', {
      name: 'profile.settings.phoneOtp.sendCode',
    }) as HTMLButtonElement;
    expect(sendButton.disabled).toBe(true);

    fireEvent.change(screen.getByLabelText('profile.settings.phoneOtp.label'), {
      target: { value: '+998abcdefgh' },
    });

    expect(sendButton.disabled).toBe(true);
    expect(authApi.requestPhoneOtp).not.toHaveBeenCalled();
  });

  it('requests an OTP for a valid phone and moves to the code screen', async () => {
    vi.mocked(authApi.requestPhoneOtp).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestPhoneOtp>>,
    );
    openPhoneLinkForm();

    fireEvent.change(screen.getByLabelText('profile.settings.phoneOtp.label'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.phoneOtp.sendCode' }));

    await waitFor(() => expect(authApi.requestPhoneOtp).toHaveBeenCalledWith('+998901234567'));
    expect(await screen.findByLabelText('profile.settings.phoneOtp.codeLabel')).toBeTruthy();
    expect(screen.getByText('profile.settings.phoneOtp.codeSent')).toBeTruthy();
    expect(authApi.linkPhone).not.toHaveBeenCalled();
  });

  it('links the phone with the OTP code, refreshes the profile and shows a success notice', async () => {
    vi.mocked(authApi.requestPhoneOtp).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestPhoneOtp>>,
    );
    vi.mocked(authApi.linkPhone).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.linkPhone>>,
    );
    openPhoneLinkForm();

    fireEvent.change(screen.getByLabelText('profile.settings.phoneOtp.label'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.phoneOtp.sendCode' }));
    await waitFor(() => expect(authApi.requestPhoneOtp).toHaveBeenCalledOnce());

    fireEvent.change(await screen.findByLabelText('profile.settings.phoneOtp.codeLabel'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.phoneOtp.confirm' }));

    await waitFor(() =>
      expect(authApi.linkPhone).toHaveBeenCalledWith({ phone: '+998901234567', code: '123456' }),
    );
    await waitFor(() => expect(refreshProfile).toHaveBeenCalledOnce());
    expect((await screen.findByRole('status')).textContent).toBe(
      'profile.settings.phoneOtp.success',
    );
    // Форма закрывается после успешной привязки.
    expect(screen.queryByLabelText('profile.settings.phoneOtp.codeLabel')).toBeNull();
    // Привязка идёт через authApi.linkPhone (OTP), а не через legacy updateProfile({phone}).
    expect(updateProfile).not.toHaveBeenCalled();
  });

  it('shows a clear message when the phone is already linked to another account (409)', async () => {
    vi.mocked(authApi.requestPhoneOtp).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestPhoneOtp>>,
    );
    vi.mocked(authApi.linkPhone).mockRejectedValue({ response: { status: 409 } });
    openPhoneLinkForm();

    fireEvent.change(screen.getByLabelText('profile.settings.phoneOtp.label'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.phoneOtp.sendCode' }));
    await waitFor(() => expect(authApi.requestPhoneOtp).toHaveBeenCalledOnce());

    fireEvent.change(await screen.findByLabelText('profile.settings.phoneOtp.codeLabel'), {
      target: { value: '123456' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.phoneOtp.confirm' }));

    expect(await screen.findByText('profile.settings.phoneOtp.taken')).toBeTruthy();
    expect(refreshProfile).not.toHaveBeenCalled();
  });

  it('shows a clear message for an invalid/expired code (401)', async () => {
    vi.mocked(authApi.requestPhoneOtp).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.requestPhoneOtp>>,
    );
    vi.mocked(authApi.linkPhone).mockRejectedValue({ response: { status: 401 } });
    openPhoneLinkForm();

    fireEvent.change(screen.getByLabelText('profile.settings.phoneOtp.label'), {
      target: { value: '+998901234567' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.phoneOtp.sendCode' }));
    await waitFor(() => expect(authApi.requestPhoneOtp).toHaveBeenCalledOnce());

    fireEvent.change(await screen.findByLabelText('profile.settings.phoneOtp.codeLabel'), {
      target: { value: '000000' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.settings.phoneOtp.confirm' }));

    expect(await screen.findByText('profile.settings.phoneOtp.invalidCode')).toBeTruthy();
    expect(refreshProfile).not.toHaveBeenCalled();
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
