import { render, screen } from '@testing-library/react';
import { Outlet } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import App from '../App';

vi.mock('./ProtectedRoute', () => ({
  ProtectedRoute: () => <Outlet />,
}));

vi.mock('../components/layout/AdminLayout', () => ({
  AdminLayout: () => <Outlet />,
}));

vi.mock('../features/dashboard/DashboardPage', () => ({
  DashboardPage: () => <h1>Рабочий стол администратора</h1>,
}));

describe('partner redemption ownership', () => {
  it('keeps redemption source only in the partner application', () => {
    const adminRedemptionModules = import.meta.glob(
      '../features/partner-redemptions/**/*.{ts,tsx,css}',
    );
    const canonicalPartnerRedeemPage = import.meta.glob(
      '../../../partner/src/pages/RedeemPage.tsx',
    );

    expect(Object.keys(adminRedemptionModules)).toEqual([]);
    expect(Object.keys(canonicalPartnerRedeemPage)).toEqual([
      '../../../partner/src/pages/RedeemPage.tsx',
    ]);
  });

  it('does not expose a redemption screen through the admin /redeem URL', async () => {
    window.history.pushState({}, '', '/redeem');

    render(<App />);

    expect(await screen.findByRole('heading', { name: 'Рабочий стол администратора' }))
      .toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'Погашение купонов' })).toBeNull();
  });
});
