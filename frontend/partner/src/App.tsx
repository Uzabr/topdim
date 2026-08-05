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

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { retry: 1, refetchOnWindowFocus: false },
  },
});

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = localStorage.getItem('token');
  if (!token) return <Navigate to="/login" replace />;
  return <>{children}</>;
}

function isCashierRole(): boolean {
  try {
    const raw = localStorage.getItem('partnerContext');
    if (raw) {
      const ctx = JSON.parse(raw) as { role?: string };
      return ctx.role === 'CASHIER';
    }
  } catch { /* ignore */ }
  return false;
}

function SmartHome() {
  if (isCashierRole()) return <Navigate to="/redeem" replace />;
  return <DashboardPage />;
}

function OwnerOnly({ children }: { children: React.ReactNode }) {
  if (isCashierRole()) return <Navigate to="/redeem" replace />;
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
                <Route path="coupons" element={<OwnerOnly><CouponsPage /></OwnerOnly>} />
                <Route path="coupons/new" element={<OwnerOnly><CouponRequestFormPage /></OwnerOnly>} />
                <Route path="coupons/:id/edit" element={<OwnerOnly><CouponRequestFormPage /></OwnerOnly>} />
                <Route path="coupons/:id/review" element={<OwnerOnly><CouponApprovalPage /></OwnerOnly>} />
                <Route path="redeem" element={<RedeemPage />} />
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
