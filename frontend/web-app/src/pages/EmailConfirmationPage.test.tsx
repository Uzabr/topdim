// @vitest-environment jsdom
import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { authApi } from '../api/auth';
import { advanceSessionGeneration } from '../sessionCleanup';
import EmailConfirmationPage from './EmailConfirmationPage';

const { refreshProfile } = vi.hoisted(() => ({ refreshProfile: vi.fn() }));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../api/auth', () => ({
  authApi: { confirmEmail: vi.fn() },
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: () => ({ isAuthenticated: true, refreshProfile }),
}));

function renderPage(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/:lang/confirm-email" element={<EmailConfirmationPage />} />
      </Routes>
    </MemoryRouter>,
  );
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

describe('EmailConfirmationPage', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(authApi.confirmEmail).mockReset();
    refreshProfile.mockReset();
    refreshProfile.mockResolvedValue(undefined);
  });

  it('confirms query token automatically and refreshes authenticated profile', async () => {
    vi.mocked(authApi.confirmEmail).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.confirmEmail>>,
    );

    renderPage('/ru/confirm-email?token=email-token');

    await waitFor(() => expect(authApi.confirmEmail).toHaveBeenCalledWith('email-token'));
    expect(refreshProfile).toHaveBeenCalledOnce();
    expect((await screen.findByRole('status')).textContent).toBe(
      'profile.emailConfirmation.success',
    );
  });

  it('prompts to open the email link when no token is present', () => {
    renderPage('/ru/confirm-email');

    expect(authApi.confirmEmail).not.toHaveBeenCalled();
    expect(screen.getByRole('alert').textContent).toBe(
      'profile.emailConfirmation.missingToken',
    );
  });

  it('surfaces backend errors when the link token is invalid', async () => {
    vi.mocked(authApi.confirmEmail).mockRejectedValue({
      response: { data: { message: 'Недействительный или просроченный токен подтверждения' } },
    });
    renderPage('/uz/confirm-email?token=expired-token');

    await waitFor(() => expect(authApi.confirmEmail).toHaveBeenCalledWith('expired-token'));
    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'Недействительный или просроченный токен подтверждения',
    );
    expect(refreshProfile).not.toHaveBeenCalled();
  });

  it('does not refresh account B or show success when account A confirmation resolves late', async () => {
    const lateConfirmation = deferred<Awaited<ReturnType<typeof authApi.confirmEmail>>>();
    vi.mocked(authApi.confirmEmail).mockReturnValue(lateConfirmation.promise);
    renderPage('/ru/confirm-email?token=account-a-token');

    await waitFor(() => expect(authApi.confirmEmail).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      lateConfirmation.resolve({} as Awaited<ReturnType<typeof authApi.confirmEmail>>);
    });

    expect(refreshProfile).not.toHaveBeenCalled();
    expect(screen.queryByText('profile.emailConfirmation.success')).toBeNull();
    expect(screen.getByText('profile.emailConfirmation.confirming')).toBeTruthy();
  });

  it('does not show an account A error or clear loading when confirmation rejects late', async () => {
    const lateConfirmation = deferred<Awaited<ReturnType<typeof authApi.confirmEmail>>>();
    vi.mocked(authApi.confirmEmail).mockReturnValue(lateConfirmation.promise);
    renderPage('/uz/confirm-email?token=account-a-token');
    await waitFor(() => expect(authApi.confirmEmail).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      lateConfirmation.reject({
        response: { data: { message: 'Account A confirmation error' } },
      });
    });

    expect(screen.queryByText('Account A confirmation error')).toBeNull();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(screen.getByText('profile.emailConfirmation.confirming')).toBeTruthy();
  });

  it('does not show success when the session switches during profile refresh', async () => {
    const lateRefresh = deferred<void>();
    vi.mocked(authApi.confirmEmail).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.confirmEmail>>,
    );
    refreshProfile.mockReturnValue(lateRefresh.promise);
    renderPage('/ru/confirm-email?token=account-a-token');

    await waitFor(() => expect(refreshProfile).toHaveBeenCalledOnce());

    advanceSessionGeneration();
    await act(async () => {
      lateRefresh.resolve();
    });

    expect(screen.queryByText('profile.emailConfirmation.success')).toBeNull();
    expect(screen.getByText('profile.emailConfirmation.confirming')).toBeTruthy();
  });
});
