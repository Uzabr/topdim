import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App as AntApp } from 'antd';
import ruRU from 'antd/locale/ru_RU';
import PartnerLayout from './layouts/PartnerLayout';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import CouponsPage from './pages/CouponsPage';
import CouponRequestFormPage from './pages/CouponRequestFormPage';
import CouponApprovalPage from './pages/CouponApprovalPage';
import RedeemPage from './pages/RedeemPage';
import StaffPage from './pages/StaffPage';
import { readPartnerContext } from './authSession';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('token');
  const partnerContext = readPartnerContext();
  if (!token || !partnerContext) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function SmartHome() {
  const ctx = readPartnerContext();
  if (!ctx) return <Navigate to="/login" replace />;
  if (ctx.canViewDashboard) return <DashboardPage />;
  if (ctx.canRedeem) return <Navigate to="/redeem" replace />;
  return <Navigate to="/login" replace />;
}

function DashboardAccessOnly({ children }: { children: React.ReactNode }) {
  const ctx = readPartnerContext();
  if (!ctx) return <Navigate to="/login" replace />;
  if (!ctx.canViewDashboard) return <Navigate to="/" replace />;
  return <>{children}</>;
}

function OwnerOnly({ children }: { children: React.ReactNode }) {
  const ctx = readPartnerContext();
  if (!ctx) return <Navigate to="/login" replace />;
  if (ctx.role !== 'OWNER') return <Navigate to="/" replace />;
  return <>{children}</>;
}

function RedeemAccessOnly({ children }: { children: React.ReactNode }) {
  const ctx = readPartnerContext();
  if (!ctx) return <Navigate to="/login" replace />;
  if (!ctx.canRedeem) return <Navigate to="/" replace />;
  return <>{children}</>;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        locale={ruRU}
        theme={{
          token: {
            colorPrimary: '#1677ff',
            borderRadius: 8,
            fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif",
          },
        }}
      >
        <AntApp>
          <BrowserRouter>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route
                path="/"
                element={
                  <PrivateRoute>
                    <PartnerLayout />
                  </PrivateRoute>
                }
              >
                <Route index element={<SmartHome />} />
                <Route path="coupons" element={<DashboardAccessOnly><CouponsPage /></DashboardAccessOnly>} />
                <Route path="coupons/new" element={<DashboardAccessOnly><CouponRequestFormPage /></DashboardAccessOnly>} />
                <Route path="coupons/:id/edit" element={<DashboardAccessOnly><CouponRequestFormPage /></DashboardAccessOnly>} />
                <Route path="coupons/:id/review" element={<DashboardAccessOnly><CouponApprovalPage /></DashboardAccessOnly>} />
                <Route path="redeem" element={<RedeemAccessOnly><RedeemPage /></RedeemAccessOnly>} />
                <Route path="staff" element={<OwnerOnly><StaffPage /></OwnerOnly>} />
              </Route>
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </BrowserRouter>
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>
  );
}
