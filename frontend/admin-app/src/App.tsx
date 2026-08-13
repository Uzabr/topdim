import { lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp, ConfigProvider, Spin } from 'antd';
import ruRU from 'antd/locale/ru_RU';

import { AdminLayout } from './components/layout/AdminLayout';
import { ProtectedRoute } from './routes/ProtectedRoute';
import type { UserRole } from './types';

const LoginPage = lazy(() => import('./features/auth/LoginPage')
  .then((module) => ({ default: module.LoginPage })));
const ForbiddenPage = lazy(() => import('./features/auth/ForbiddenPage')
  .then((module) => ({ default: module.ForbiddenPage })));
const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage')
  .then((module) => ({ default: module.DashboardPage })));
const PartnerApplicationsPage = lazy(() => import('./features/partners/PartnerApplicationsPage')
  .then((module) => ({ default: module.PartnerApplicationsPage })));
const MerchantProfileChangeQueuePage = lazy(() => import('./features/merchant-profile-changes/MerchantProfileChangeQueuePage')
  .then((module) => ({ default: module.MerchantProfileChangeQueuePage })));
const MerchantProfileChangeDetailPage = lazy(() => import('./features/merchant-profile-changes/MerchantProfileChangeDetailPage')
  .then((module) => ({ default: module.MerchantProfileChangeDetailPage })));
const CouponFormPage = lazy(() => import('./features/coupons/CouponFormPage')
  .then((module) => ({ default: module.CouponFormPage })));
const CouponWorkspacePage = lazy(() => import('./features/coupons/workspace/CouponWorkspacePage')
  .then((module) => ({ default: module.CouponWorkspacePage })));
const LegacyCouponRedirect = lazy(() => import('./features/coupons/workspace/LegacyCouponRedirect')
  .then((module) => ({ default: module.LegacyCouponRedirect })));
const CategoriesPage = lazy(() => import('./features/catalog/CategoriesPage')
  .then((module) => ({ default: module.CategoriesPage })));
const StaffPage = lazy(() => import('./features/system/StaffPage')
  .then((module) => ({ default: module.StaffPage })));
const AuditLogPage = lazy(() => import('./features/system/AuditLogPage')
  .then((module) => ({ default: module.AuditLogPage })));
const MerchantsPage = lazy(() => import('./features/merchants/MerchantsPage')
  .then((module) => ({ default: module.MerchantsPage })));
const MerchantDetailPage = lazy(() => import('./features/merchants/MerchantDetailPage')
  .then((module) => ({ default: module.MerchantDetailPage })));
const OrdersPage = lazy(() => import('./features/orders/OrdersPage')
  .then((module) => ({ default: module.OrdersPage })));
const PurchasedCouponLookupPage = lazy(() => import('./features/orders/PurchasedCouponLookupPage')
  .then((module) => ({ default: module.PurchasedCouponLookupPage })));
const ReviewsPage = lazy(() => import('./features/support/ReviewsPage')
  .then((module) => ({ default: module.ReviewsPage })));
const RefundsPage = lazy(() => import('./features/support/RefundsPage')
  .then((module) => ({ default: module.RefundsPage })));
const ComplaintsPage = lazy(() => import('./features/support/ComplaintsPage')
  .then((module) => ({ default: module.ComplaintsPage })));
const UsersListPage = lazy(() => import('./features/users/UsersListPage')
  .then((module) => ({ default: module.UsersListPage })));

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
            <Suspense
              fallback={<Spin aria-label="Загрузка страницы" size="large" style={{ display: 'block', margin: 80 }} />}
            >
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
                  <Route path="/merchants/profile-changes" element={<MerchantProfileChangeQueuePage />} />
                  <Route path="/merchants/profile-changes/:id" element={<MerchantProfileChangeDetailPage />} />

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
            </Suspense>
          </BrowserRouter>
        </AntApp>
      </QueryClientProvider>
    </ConfigProvider>
  );
}

export default App;
