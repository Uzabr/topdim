// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { authApi } from '../api/auth';
import ResetPasswordPage from './ResetPasswordPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../api/auth', () => ({
  authApi: { confirmPasswordReset: vi.fn() },
}));

function renderPage(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/:lang/reset-password" element={<ResetPasswordPage />} />
      </Routes>
    </MemoryRouter>,
  );
}

const STRONG_PASSWORD = 'Strong1!';

describe('ResetPasswordPage', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(authApi.confirmPasswordReset).mockReset();
  });

  it('reads the token from the URL and submits it with the new password', async () => {
    vi.mocked(authApi.confirmPasswordReset).mockResolvedValue(
      {} as Awaited<ReturnType<typeof authApi.confirmPasswordReset>>,
    );

    renderPage('/ru/reset-password?token=reset-token');

    fireEvent.change(screen.getByLabelText('resetPasswordPage.newPassword'), {
      target: { value: STRONG_PASSWORD },
    });
    fireEvent.change(screen.getByLabelText('resetPasswordPage.confirmPassword'), {
      target: { value: STRONG_PASSWORD },
    });
    fireEvent.click(screen.getByRole('button', { name: 'resetPasswordPage.submit' }));

    await waitFor(() =>
      expect(authApi.confirmPasswordReset).toHaveBeenCalledWith({
        token: 'reset-token',
        newPassword: STRONG_PASSWORD,
        confirmPassword: STRONG_PASSWORD,
      }),
    );
    expect((await screen.findByRole('status')).textContent).toBe(
      'resetPasswordPage.success',
    );
  });

  it('shows a message and no form when the URL has no token', () => {
    renderPage('/uz/reset-password');

    expect(screen.getByRole('alert').textContent).toBe(
      'resetPasswordPage.missingToken',
    );
    expect(screen.queryByLabelText('resetPasswordPage.newPassword')).toBeNull();
  });

  it('shows a mismatch error and does not call the API when passwords differ', () => {
    renderPage('/ru/reset-password?token=reset-token');

    fireEvent.change(screen.getByLabelText('resetPasswordPage.newPassword'), {
      target: { value: STRONG_PASSWORD },
    });
    fireEvent.change(screen.getByLabelText('resetPasswordPage.confirmPassword'), {
      target: { value: 'Different1!' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'resetPasswordPage.submit' }));

    expect(screen.getByRole('alert').textContent).toBe('resetPasswordPage.mismatch');
    expect(authApi.confirmPasswordReset).not.toHaveBeenCalled();
  });

  it('surfaces the backend error for an invalid or expired token', async () => {
    vi.mocked(authApi.confirmPasswordReset).mockRejectedValue({
      response: { data: { message: 'Недействительный или просроченный токен' } },
    });

    renderPage('/ru/reset-password?token=expired-token');

    fireEvent.change(screen.getByLabelText('resetPasswordPage.newPassword'), {
      target: { value: STRONG_PASSWORD },
    });
    fireEvent.change(screen.getByLabelText('resetPasswordPage.confirmPassword'), {
      target: { value: STRONG_PASSWORD },
    });
    fireEvent.click(screen.getByRole('button', { name: 'resetPasswordPage.submit' }));

    expect(await screen.findByRole('alert')).toHaveProperty(
      'textContent',
      'Недействительный или просроченный токен',
    );
  });
});
