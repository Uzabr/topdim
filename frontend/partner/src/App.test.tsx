import { render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';

vi.mock('./pages/DashboardPage', () => ({
  default: () => <div>Dashboard content</div>,
}));
vi.mock('./pages/StaffPage', () => ({
  default: () => <div>Staff content</div>,
}));
vi.mock('./pages/RedeemPage', () => ({
  default: () => <div>Redeem content</div>,
}));

describe('partner route authorization', () => {
  beforeEach(() => {
    localStorage.clear();
    window.history.replaceState({}, '', '/');
  });

  it('redirects a token-only session without partner context to login', async () => {
    localStorage.setItem('token', 'access-token-without-context');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Кабинет партнёра' })).toBeTruthy();
    expect(screen.queryByText('Dashboard content')).toBeNull();
  });

  it('redirects a session with malformed partner context to login', async () => {
    localStorage.setItem('token', 'access-token');
    localStorage.setItem('partnerContext', JSON.stringify({ role: 'OWNER' }));

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Кабинет партнёра' })).toBeTruthy();
    expect(screen.queryByText('Dashboard content')).toBeNull();
  });

  it('does not allow a manager to open owner staff management directly', async () => {
    window.history.replaceState({}, '', '/staff');
    localStorage.setItem('token', 'manager-access-token');
    localStorage.setItem('partnerContext', JSON.stringify({
      role: 'MANAGER',
      merchantId: 8,
      canViewDashboard: true,
      canRedeem: true,
    }));

    render(<App />);

    expect(await screen.findByText('Dashboard content')).toBeTruthy();
    expect(screen.queryByText('Staff content')).toBeNull();
  });

  it('does not open redemption by direct URL when permission is disabled', async () => {
    window.history.replaceState({}, '', '/redeem');
    localStorage.setItem('token', 'owner-access-token');
    localStorage.setItem('partnerContext', JSON.stringify({
      role: 'OWNER',
      merchantId: 8,
      canViewDashboard: true,
      canRedeem: false,
    }));

    render(<App />);

    expect(await screen.findByText('Dashboard content')).toBeTruthy();
    expect(screen.queryByText('Redeem content')).toBeNull();
  });
});
