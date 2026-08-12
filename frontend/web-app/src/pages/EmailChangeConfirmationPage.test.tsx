// @vitest-environment jsdom
import { cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { authApi } from '../api/auth';
import EmailChangeConfirmationPage from './EmailChangeConfirmationPage';

const { refreshProfile } = vi.hoisted(() => ({ refreshProfile: vi.fn() }));

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../api/auth', () => ({
  authApi: { confirmEmailChange: vi.fn() },
}));

vi.mock('../store/authStore', () => ({
  useAuthStore: () => ({ isAuthenticated: true, refreshProfile }),
}));

function renderPage(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/:lang/confirm-email-change" element={<EmailChangeConfirmationPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('EmailChangeConfirmationPage', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(authApi.confirmEmailChange).mockReset();
    refreshProfile.mockReset();
    refreshProfile.mockResolvedValue(undefined);
  });

  it('confirms query token automatically and refreshes authenticated profile', async () => {
    vi.mocked(authApi.confirmEmailChange).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.confirmEmailChange>>,
    );

    renderPage('/ru/confirm-email-change?token=change-token');

    await waitFor(() =>
      expect(authApi.confirmEmailChange).toHaveBeenCalledWith('change-token'),
    );
    expect(refreshProfile).toHaveBeenCalledOnce();
    expect((await screen.findByRole('status')).textContent).toBe(
      'profile.emailChangeConfirmation.success',
    );
  });

  it('surfaces a backend error message for an expired link token', async () => {
    vi.mocked(authApi.confirmEmailChange).mockRejectedValue({
      response: { status: 401, data: { message: 'Недействительный или просроченный токен подтверждения' } },
    });
    renderPage('/uz/confirm-email-change?token=expired-token');

    await waitFor(() =>
      expect(authApi.confirmEmailChange).toHaveBeenCalledWith('expired-token'),
    );
    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'Недействительный или просроченный токен подтверждения',
    );
    expect(refreshProfile).not.toHaveBeenCalled();
  });

  it('shows a taken-email message when the backend responds 409 without a message body', async () => {
    vi.mocked(authApi.confirmEmailChange).mockRejectedValue({
      response: { status: 409 },
    });
    renderPage('/ru/confirm-email-change?token=taken-token');

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'profile.emailChangeConfirmation.taken',
    );
  });

  it('prompts to open the email link when no token is present', () => {
    renderPage('/ru/confirm-email-change');

    expect(authApi.confirmEmailChange).not.toHaveBeenCalled();
    expect(screen.getByRole('alert').textContent).toBe(
      'profile.emailChangeConfirmation.missingToken',
    );
  });
});
