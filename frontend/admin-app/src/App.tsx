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

import { CouponsListPage } from './features/coupons/CouponsListPage';
import { CouponFormPage } from './features/coupons/CouponFormPage';
import { CouponKanbanPage } from './features/coupons/CouponKanbanPage';
import { MerchantReviewPage } from './features/coupons/MerchantReviewPage';
import { CategoriesPage } from './features/catalog/CategoriesPage';
import { StaffPage } from './features/system/StaffPage';
import { AuditLogPage } from './features/system/AuditLogPage';
import { MerchantsPage } from './features/merchants/MerchantsPage';
import { MerchantDetailPage } from './features/merchants/MerchantDetailPage';

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
              <Route element={<ProtectedRoute allowedRoles={['MODERATOR', 'ADMIN', 'SUPER_ADMIN']} />}>
                <Route element={<AdminLayout />}>
                  <Route path="/dashboard" element={<DashboardPage />} />

                  {/* Управление купонами (MODERATOR, ADMIN, SUPER_ADMIN) */}
                  <Route path="/moderation/coupons" element={<CouponsListPage />} />
                  <Route path="/moderation/coupons/kanban" element={<CouponKanbanPage />} />
                  <Route path="/moderation/coupons/create" element={<CouponFormPage />} />
                  <Route path="/moderation/coupons/edit/:id" element={<CouponFormPage />} />
                  <Route path="/moderation/coupons/review" element={<MerchantReviewPage />} />
                  
                  {/* Поддержка */}
                  {/* TODO: подключить ComplaintsPage, ReviewsPage */}

                  {/* Справочники (только ADMIN, SUPER_ADMIN) */}
                  <Route path="/catalog/categories" element={<CategoriesPage />} />
                  <Route path="/catalog/merchants" element={<MerchantsPage />} />
                  <Route path="/catalog/merchants/:id" element={<MerchantDetailPage />} />
                  {/* TODO: подключить BazaarsPage, ShopsPage */}

                  {/* Заказы (только ADMIN, SUPER_ADMIN) */}
                  {/* TODO: подключить OrdersPage, PromocodesPage */}

                  {/* Пользователи (ADMIN, SUPER_ADMIN) */}
                  <Route path="/users/partner-applications" element={<PartnerApplicationsPage />} />
                  {/* TODO: подключить UsersListPage */}
                </Route>
              </Route>

              {/* Системные роуты: только SUPER_ADMIN */}
              <Route element={<ProtectedRoute allowedRoles={['SUPER_ADMIN']} />}>
                <Route element={<AdminLayout />}>
                  <Route path="/system/staff" element={<StaffPage />} />
                  <Route path="/system/audit" element={<AuditLogPage />} />
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
