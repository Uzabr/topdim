// @vitest-environment jsdom
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import LoginPage from './LoginPage';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../components/auth/LoginCard', () => ({
  default: ({ onSuccess }: { onSuccess: () => void }) => (
    <button type="button" onClick={onSuccess}>
      mock-login-success
    </button>
  ),
}));

/** Показывает текущий pathname — так тест видит, куда навигация привела. */
function LocationSpy() {
  const location = useLocation();
  return <div data-testid="location-spy">{location.pathname}</div>;
}

function renderPage(initialEntry: { pathname: string; state?: unknown }) {
  render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/:lang/login" element={<LoginPage />} />
        <Route path="*" element={<LocationSpy />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('LoginPage', () => {
  afterEach(cleanup);

  it('returns to the intended destination (location.state.from) after successful login', () => {
    renderPage({ pathname: '/ru/login', state: { from: '/ru/checkout' } });

    fireEvent.click(screen.getByText('mock-login-success'));

    expect(screen.getByTestId('location-spy').textContent).toBe('/ru/checkout');
  });

  it('falls back to the locale home page when there is no intended destination', () => {
    renderPage({ pathname: '/ru/login' });

    fireEvent.click(screen.getByText('mock-login-success'));

    expect(screen.getByTestId('location-spy').textContent).toBe('/ru/');
  });
});
