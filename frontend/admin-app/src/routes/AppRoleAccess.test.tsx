import { render, screen } from '@testing-library/react';
import { Outlet } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from '../App';
import { useAuthStore } from '../store/authStore';
import type { UserRole } from '../types';

vi.mock('../components/layout/AdminLayout', () => ({
  AdminLayout: () => <Outlet />,
}));

vi.mock('../features/auth/LoginPage', () => ({ LoginPage: () => <h1>LOGIN_PAGE</h1> }));
vi.mock('../features/auth/ForbiddenPage', () => ({ ForbiddenPage: () => <h1>FORBIDDEN_PAGE</h1> }));
vi.mock('../features/dashboard/DashboardPage', () => ({ DashboardPage: () => <h1>DASHBOARD_PAGE</h1> }));
vi.mock('../features/coupons/CouponFormPage', () => ({ CouponFormPage: () => <h1>COUPON_FORM_PAGE</h1> }));
vi.mock('../features/coupons/workspace/CouponWorkspacePage', () => ({ CouponWorkspacePage: () => <h1>COUPONS_PAGE</h1> }));
vi.mock('../features/catalog/CategoriesPage', () => ({ CategoriesPage: () => <h1>CATEGORIES_PAGE</h1> }));
vi.mock('../features/merchants/MerchantsPage', () => ({ MerchantsPage: () => <h1>MERCHANTS_PAGE</h1> }));
vi.mock('../features/merchants/MerchantDetailPage', () => ({ MerchantDetailPage: () => <h1>MERCHANT_DETAIL_PAGE</h1> }));
vi.mock('../features/orders/OrdersPage', () => ({ OrdersPage: () => <h1>ORDERS_PAGE</h1> }));
vi.mock('../features/orders/PurchasedCouponLookupPage', () => ({ PurchasedCouponLookupPage: () => <h1>COUPON_LOOKUP_PAGE</h1> }));
vi.mock('../features/partners/PartnerApplicationsPage', () => ({ PartnerApplicationsPage: () => <h1>PARTNER_APPLICATIONS_PAGE</h1> }));
vi.mock('../features/merchant-profile-changes/MerchantProfileChangeQueuePage', () => ({ MerchantProfileChangeQueuePage: () => <h1>PROFILE_CHANGES_PAGE</h1> }));
vi.mock('../features/merchant-profile-changes/MerchantProfileChangeDetailPage', () => ({ MerchantProfileChangeDetailPage: () => <h1>PROFILE_CHANGE_DETAIL_PAGE</h1> }));
vi.mock('../features/users/UsersListPage', () => ({ UsersListPage: () => <h1>USERS_PAGE</h1> }));
vi.mock('../features/support/ReviewsPage', () => ({ ReviewsPage: () => <h1>REVIEWS_PAGE</h1> }));
vi.mock('../features/support/RefundsPage', () => ({ RefundsPage: () => <h1>REFUNDS_PAGE</h1> }));
vi.mock('../features/support/ComplaintsPage', () => ({ ComplaintsPage: () => <h1>COMPLAINTS_PAGE</h1> }));
vi.mock('../features/system/StaffPage', () => ({ StaffPage: () => <h1>STAFF_PAGE</h1> }));
vi.mock('../features/system/AuditLogPage', () => ({ AuditLogPage: () => <h1>AUDIT_PAGE</h1> }));

function loginAs(role: UserRole) {
  useAuthStore.getState().login('access-token', {
    id: 1,
    email: `${role.toLowerCase()}@topdim.uz`,
    phone: '+998901234567',
    firstName: role,
    lastName: 'Tester',
    role,
    avatarUrl: null,
  });
}

function renderPath(path: string) {
  window.history.pushState({}, '', path);
  return render(<App />);
}

describe('admin application role access', () => {
  beforeEach(() => {
    useAuthStore.getState().logout();
  });

  it.each([
    ['/catalog/categories', 'CATEGORIES_PAGE'],
    ['/catalog/merchants', 'MERCHANTS_PAGE'],
    ['/catalog/merchants/17', 'MERCHANT_DETAIL_PAGE'],
    ['/orders/list', 'ORDERS_PAGE'],
    ['/orders/coupon-lookup', 'COUPON_LOOKUP_PAGE'],
    ['/users/list', 'USERS_PAGE'],
    ['/users/partner-applications', 'PARTNER_APPLICATIONS_PAGE'],
    ['/support/refunds', 'REFUNDS_PAGE'],
  ])('denies MODERATOR direct access to %s', async (path, pageMarker) => {
    loginAs('MODERATOR');

    renderPath(path);

    expect(await screen.findByRole('heading', { name: 'FORBIDDEN_PAGE' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: pageMarker })).toBeNull();
  });

  it.each([
    ['/dashboard', 'DASHBOARD_PAGE'],
    ['/coupons', 'COUPONS_PAGE'],
    ['/merchants/profile-changes', 'PROFILE_CHANGES_PAGE'],
    ['/merchants/profile-changes/71', 'PROFILE_CHANGE_DETAIL_PAGE'],
    ['/support/reviews', 'REVIEWS_PAGE'],
    ['/support/complaints', 'COMPLAINTS_PAGE'],
  ])('allows MODERATOR to open %s', async (path, pageMarker) => {
    loginAs('MODERATOR');

    renderPath(path);

    expect(await screen.findByRole('heading', { name: pageMarker })).toBeTruthy();
  });

  it.each(['ADMIN', 'SUPER_ADMIN'] as UserRole[])(
    'allows %s to open an admin-only route',
    async (role) => {
      loginAs(role);

      renderPath('/orders/list');

      expect(await screen.findByRole('heading', { name: 'ORDERS_PAGE' })).toBeTruthy();
    },
  );

  it('keeps staff management exclusive to SUPER_ADMIN', async () => {
    loginAs('ADMIN');

    renderPath('/system/staff');

    expect(await screen.findByRole('heading', { name: 'FORBIDDEN_PAGE' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'STAFF_PAGE' })).toBeNull();
  });

  it('allows ADMIN to open the action journal', async () => {
    loginAs('ADMIN');

    renderPath('/system/audit');

    expect(await screen.findByRole('heading', { name: 'AUDIT_PAGE' })).toBeTruthy();
  });

  it('denies MODERATOR access to the action journal', async () => {
    loginAs('MODERATOR');

    renderPath('/system/audit');

    expect(await screen.findByRole('heading', { name: 'FORBIDDEN_PAGE' })).toBeTruthy();
    expect(screen.queryByRole('heading', { name: 'AUDIT_PAGE' })).toBeNull();
  });

  it('redirects an unauthenticated user to login', async () => {
    renderPath('/dashboard');

    expect(await screen.findByRole('heading', { name: 'LOGIN_PAGE' })).toBeTruthy();
  });
});
