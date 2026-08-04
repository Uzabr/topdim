import { useEffect } from 'react';
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
import HomePage from './pages/HomePage';
import CouponCatalogPage from './pages/CouponCatalogPage';
import CouponDetailPage from './pages/CouponDetailPage';
import CartPage from './pages/CartPage';
import CheckoutPage from './pages/CheckoutPage';
import PaymentPage from './pages/PaymentPage';
import BazaarMapPage from './pages/BazaarMapPage';
import BazaarDetailPage from './pages/BazaarDetailPage';
import ShopDetailPage from './pages/ShopDetailPage';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import ProfilePage from './pages/ProfilePage';
import SearchPage from './pages/SearchPage';
import FavoritesPage from './pages/FavoritesPage';
import NotFoundPage from './pages/NotFoundPage';
import PartnersPage from './pages/legal/PartnersPage';
import FAQPage from './pages/legal/FAQPage';
import TermsPage from './pages/legal/TermsPage';
import PrivacyPage from './pages/legal/PrivacyPage';
import EmailConfirmationPage from './pages/EmailConfirmationPage';
import EmailChangeConfirmationPage from './pages/EmailChangeConfirmationPage';
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
