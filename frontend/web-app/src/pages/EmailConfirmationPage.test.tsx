// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { authApi } from '../api/auth';
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

  it('allows manual code entry and surfaces backend errors', async () => {
    vi.mocked(authApi.confirmEmail).mockRejectedValue({
      response: { data: { message: 'Недействительный или просроченный токен подтверждения' } },
    });
    renderPage('/uz/confirm-email');

    fireEvent.change(screen.getByLabelText('profile.emailConfirmation.code'), {
      target: { value: 'expired-token' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'profile.emailConfirmation.submit' }));

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'Недействительный или просроченный токен подтверждения',
    );
    expect(refreshProfile).not.toHaveBeenCalled();
  });
});
