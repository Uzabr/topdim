import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider } from 'antd';
import ruRU from 'antd/locale/ru_RU';

import { AdminLayout } from './components/layout/AdminLayout';
import { ProtectedRoute } from './routes/ProtectedRoute';
import { LoginPage } from './features/auth/LoginPage';
import { ForbiddenPage } from './features/auth/ForbiddenPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { PartnerApplicationsPage } from './features/partners/PartnerApplicationsPage';

import { CouponFormPage } from './features/coupons/CouponFormPage';
import { CouponWorkspacePage } from './features/coupons/workspace/CouponWorkspacePage';
import { LegacyCouponRedirect } from './features/coupons/workspace/LegacyCouponRedirect';
import { CategoriesPage } from './features/catalog/CategoriesPage';
import { StaffPage } from './features/system/StaffPage';
import { AuditLogPage } from './features/system/AuditLogPage';
import { MerchantsPage } from './features/merchants/MerchantsPage';
import { MerchantDetailPage } from './features/merchants/MerchantDetailPage';
import { OrdersPage } from './features/orders/OrdersPage';
import { PurchasedCouponLookupPage } from './features/orders/PurchasedCouponLookupPage';
import { ReviewsPage } from './features/support/ReviewsPage';
import { RefundsPage } from './features/support/RefundsPage';
import { ComplaintsPage } from './features/support/ComplaintsPage';
import { UsersListPage } from './features/users/UsersListPage';
import type { UserRole } from './types';

const STAFF_ROLES: UserRole[] = ['MODERATOR', 'ADMIN', 'SUPER_ADMIN'];
const ADMIN_ROLES: UserRole[] = ['ADMIN', 'SUPER_ADMIN'];
const SUPER_ADMIN_ROLES: UserRole[] = ['SUPER_ADMIN'];

function HomeRedirect() {
  return <Navigate to="/dashboard" replace />;
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
      staleTime: 30_000,
    },
  },
});

function App() {
  return (
    <ConfigProvider
      locale={ruRU}
      theme={{
        token: {
          colorPrimary: '#1890ff',
          borderRadius: 6,
          fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, sans-serif",
        },
      }}
    >
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <BrowserRouter>
            <Routes>
              {/* Публичные роуты */}
              <Route path="/login" element={<LoginPage />} />
              <Route path="/403" element={<ForbiddenPage />} />

              {/* Защищённые роуты: Все сотрудники */}
              <Route element={<ProtectedRoute allowedRoles={STAFF_ROLES} />}>
                <Route element={<AdminLayout />}>
                  <Route path="/dashboard" element={<DashboardPage />} />

                  {/* Управление купонами (MODERATOR, ADMIN, SUPER_ADMIN) */}
                  <Route path="/coupons" element={<CouponWorkspacePage />} />
                  <Route path="/coupons/new" element={<CouponFormPage />} />
                  <Route path="/coupons/:id/edit" element={<CouponFormPage />} />

                  {/* Совместимость сохранённых ссылок на старые экраны купонов */}
                  <Route path="/moderation/coupons" element={<LegacyCouponRedirect target="workspace" />} />
                  <Route path="/moderation/coupons/kanban" element={<LegacyCouponRedirect target="kanban" />} />
                  <Route path="/moderation/requests" element={<LegacyCouponRedirect target="new-tab" />} />
                  <Route path="/moderation/coupons/create" element={<LegacyCouponRedirect target="create" />} />
                  <Route path="/moderation/coupons/edit/:id" element={<LegacyCouponRedirect target="edit" />} />
                  <Route path="/moderation/coupons/review" element={<LegacyCouponRedirect target="waiting-partner" />} />

                  {/* Поддержка */}
                  <Route path="/support/reviews" element={<ReviewsPage />} />
                  <Route path="/support/complaints" element={<ComplaintsPage />} />

                  {/* Административные роуты: только ADMIN, SUPER_ADMIN */}
                  <Route element={<ProtectedRoute allowedRoles={ADMIN_ROLES} />}>
                    <Route path="/support/refunds" element={<RefundsPage />} />

                    <Route path="/catalog/categories" element={<CategoriesPage />} />
                    <Route path="/catalog/merchants" element={<MerchantsPage />} />
                    <Route path="/catalog/merchants/:id" element={<MerchantDetailPage />} />

                    <Route path="/orders/list" element={<OrdersPage />} />
                    <Route path="/orders/coupon-lookup" element={<PurchasedCouponLookupPage />} />

                    <Route path="/users/partner-applications" element={<PartnerApplicationsPage />} />
                    <Route path="/users/list" element={<UsersListPage />} />
                    <Route path="/system/audit" element={<AuditLogPage />} />
                  </Route>
                </Route>
              </Route>

              {/* Системные роуты: только SUPER_ADMIN */}
              <Route element={<ProtectedRoute allowedRoles={SUPER_ADMIN_ROLES} />}>
                <Route element={<AdminLayout />}>
                  <Route path="/system/staff" element={<StaffPage />} />
                </Route>
              </Route>


              {/* Корневой редирект */}
              <Route path="/" element={<HomeRedirect />} />
              <Route path="*" element={<HomeRedirect />} />
            </Routes>
          </BrowserRouter>
        </AntApp>
      </QueryClientProvider>
    </ConfigProvider>
  );
}

export default App;
