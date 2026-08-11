import { useEffect, lazy, Suspense } from 'react';
import { BrowserRouter, Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { QueryClientProvider } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useAuthStore } from './store/authStore';
import { hidesBottomNav } from './utils/mobileScreens';
import ScrollToTop from './components/ScrollToTop';
import LocaleLayout from './components/LocaleLayout';
import Header from './components/layout/Header';
import BottomNav from './components/layout/BottomNav';
import MobileBackdrop from './components/layout/MobileBackdrop';
import Footer from './components/layout/Footer';
import CartDrawer from './components/cart/CartDrawer';
import CookieConsent from './components/ui/CookieConsent';
import LimitModal from './components/ui/LimitModal';
// Частые входы — грузим сразу (без флеша фолбэка на первой загрузке).
import HomePage from './pages/HomePage';
import CouponCatalogPage from './pages/CouponCatalogPage';
import LoginPage from './pages/LoginPage';

// Остальное — lazy: динамический import() уводит код (и его тяжёлые
// транзитивные зависимости) в отдельные чанки, которые грузятся ТОЛЬКО при
// переходе на маршрут. Так с главной уходят three/@react-three/framer/lenis
// (страница /partners) и @2gis/mapgl (карты и деталь купона → WhereSection).
const CouponDetailPage = lazy(() => import('./pages/CouponDetailPage'));
const CartPage = lazy(() => import('./pages/CartPage'));
const CheckoutPage = lazy(() => import('./pages/CheckoutPage'));
const PaymentPage = lazy(() => import('./pages/PaymentPage'));
const BazaarMapPage = lazy(() => import('./pages/BazaarMapPage'));
const BazaarDetailPage = lazy(() => import('./pages/BazaarDetailPage'));
const ShopDetailPage = lazy(() => import('./pages/ShopDetailPage'));
const RegisterPage = lazy(() => import('./pages/RegisterPage'));
const ResetPasswordPage = lazy(() => import('./pages/ResetPasswordPage'));
const ProfilePage = lazy(() => import('./pages/ProfilePage'));
const SearchPage = lazy(() => import('./pages/SearchPage'));
const FavoritesPage = lazy(() => import('./pages/FavoritesPage'));
const NotFoundPage = lazy(() => import('./pages/NotFoundPage'));
const PartnersPage = lazy(() => import('./pages/legal/PartnersPage'));
const FAQPage = lazy(() => import('./pages/legal/FAQPage'));
const TermsPage = lazy(() => import('./pages/legal/TermsPage'));
const PrivacyPage = lazy(() => import('./pages/legal/PrivacyPage'));
const EmailConfirmationPage = lazy(() => import('./pages/EmailConfirmationPage'));
const EmailChangeConfirmationPage = lazy(() => import('./pages/EmailChangeConfirmationPage'));
import { queryClient } from './queryClient';

/** Redirect bare "/" to "/:lang/" */
function RootRedirect() {
  const { i18n } = useTranslation();
  const lang = i18n.language?.substring(0, 2) || 'ru';
  return <Navigate to={`/${lang}`} replace />;
}

function AppShell() {
  const location = useLocation();
  const isPartnerLanding = /^\/(ru|uz)\/partners\/?$/.test(location.pathname);
  const isLoginPage = /^\/(ru|uz)\/login\/?$/.test(location.pathname);
  // Купон/корзина/оплата/поиск: снизу своя кнопка — таблетка навигации налезала бы.
  const noBottomNav = hidesBottomNav(location.pathname);

  useEffect(() => {
    if (isPartnerLanding) {
      document.body.classList.add('td-partner-body');
      document.documentElement.classList.add('td-partner-body');
    } else {
      document.body.classList.remove('td-partner-body');
      document.documentElement.classList.remove('td-partner-body');
    }

    if (isLoginPage) {
      document.body.classList.add('td-login-body');
      document.documentElement.classList.add('td-login-body');
    } else {
      document.body.classList.remove('td-login-body');
      document.documentElement.classList.remove('td-login-body');
    }
  }, [isPartnerLanding, isLoginPage]);

  return (
    <div className="app-shell">
      {!isPartnerLanding && <MobileBackdrop />}
      {!isPartnerLanding && <Header />}
      <main className={`app-main${isLoginPage ? ' app-main--login' : ''}`}>
      <Suspense fallback={<div className="route-fallback" style={{ minHeight: '60vh' }} />}>
      <Routes>
        {/* Bare root → redirect to /ru or /uz */}
        <Route path="/" element={<RootRedirect />} />

        {/* All pages under /:lang */}
        <Route path="/:lang" element={<LocaleLayout />}>
          <Route index element={<HomePage />} />
          <Route path="coupons" element={<CouponCatalogPage />} />
          <Route path="coupons/:id" element={<CouponDetailPage />} />
          <Route path="cart" element={<CartPage />} />
          <Route path="checkout" element={<CheckoutPage />} />
          <Route path="payment/:orderId" element={<PaymentPage />} />
          <Route path="bazaar" element={<BazaarMapPage />} />
          <Route path="bazaar/:id" element={<BazaarDetailPage />} />
          <Route path="shops/:id" element={<ShopDetailPage />} />
          <Route path="login" element={<LoginPage />} />
          <Route path="register" element={<RegisterPage />} />
          <Route path="reset-password" element={<ResetPasswordPage />} />
          <Route path="profile" element={<ProfilePage />} />
          <Route path="confirm-email" element={<EmailConfirmationPage />} />
          <Route path="confirm-email-change" element={<EmailChangeConfirmationPage />} />
          <Route path="search" element={<SearchPage />} />
          <Route path="favorites" element={<FavoritesPage />} />
          <Route path="partners" element={<PartnersPage />} />
          <Route path="faq" element={<FAQPage />} />
          <Route path="terms" element={<TermsPage />} />
          <Route path="privacy" element={<PrivacyPage />} />
        </Route>

        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      </Suspense>
      </main>
      {!isPartnerLanding && !isLoginPage && <Footer />}
      {!isPartnerLanding && !isLoginPage && !noBottomNav && <BottomNav />}
      <CartDrawer />
      <LimitModal />
      <CookieConsent />
    </div>
  );
}

function AppContent() {
  const { loadFromStorage } = useAuthStore();

  useEffect(() => {
    loadFromStorage();
  }, [loadFromStorage]);

  return (
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <ScrollToTop />
      <AppShell />
    </BrowserRouter>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AppContent />
    </QueryClientProvider>
  );
}
