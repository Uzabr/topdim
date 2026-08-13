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
vi.mock('./pages/RedemptionHistoryPage', () => ({
  default: () => <div>History content</div>,
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

  it.each([
    ['OWNER', true, true],
    ['OWNER', true, false],
    ['MANAGER', true, true],
    ['CASHIER', false, true],
    ['CASHIER', false, false],
  ])('opens read-only history for %s with dashboard=%s and redeem=%s', async (
    role,
    canViewDashboard,
    canRedeem,
  ) => {
    window.history.replaceState({}, '', '/redemptions');
    localStorage.setItem('token', `${role}-token`);
    localStorage.setItem('partnerContext', JSON.stringify({
      role,
      merchantId: 8,
      ...(role === 'CASHIER' ? { merchantLocationId: 21, staffId: 15 } : {}),
      canViewDashboard,
      canRedeem,
    }));

    render(<App />);

    expect(await screen.findByText('History content')).toBeTruthy();
    expect(screen.getByText('История погашений')).toBeTruthy();
  });

  it.each(['OWNER', 'MANAGER'] as const)(
    'opens company routes and menu for %s',
    async (role) => {
      window.history.replaceState({}, '', '/company/requests/17');
      localStorage.setItem('token', `${role}-token`);
      localStorage.setItem('partnerContext', JSON.stringify({
        role,
        merchantId: 8,
        canViewDashboard: true,
        canRedeem: true,
      }));

      render(<App />);

      expect(await screen.findByRole('region', { name: 'Раздел компании' })).toBeTruthy();
      expect(screen.getByRole('menuitem', { name: /Моя компания/ })
        .classList.contains('ant-menu-item-selected')).toBe(true);
      expect(window.location.pathname).toBe('/company/requests/17');
    },
  );

  it('redirects CASHIER away from a direct company URL and hides the menu item', async () => {
    window.history.replaceState({}, '', '/company');
    localStorage.setItem('token', 'cashier-token');
    localStorage.setItem('partnerContext', JSON.stringify({
      role: 'CASHIER',
      merchantId: 8,
      merchantLocationId: 21,
      staffId: 15,
      canViewDashboard: false,
      canRedeem: true,
    }));

    render(<App />);

    expect(await screen.findByText('Redeem content')).toBeTruthy();
    expect(screen.queryByText('Моя компания')).toBeNull();
    expect(screen.queryByRole('region', { name: 'Раздел компании' })).toBeNull();
    expect(window.location.pathname).toBe('/redeem');
  });
});
