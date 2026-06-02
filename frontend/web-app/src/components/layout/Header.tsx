import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Heart, MapPinned, Menu, Search, ShoppingBag, Ticket, User, X } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import { useFavoritesStore } from '../../store/favoritesStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import { notificationsApi } from '../../api/notifications';
import LanguageSelector from '../ui/LanguageSelector';
import { BAZAAR_NAV_ENABLED } from '../../config/features';

const MOBILE_MENU_ICON = 22;
import './Header.css';

export default function Header() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const { totalItems, toggleCart } = useCartStore();
  const { favoriteIds } = useFavoritesStore();
  const { isAuthenticated, user } = useAuthStore();
  const { t } = useTranslation();
  const lp = useLocalePath();

  // Unread notifications badge
  const { data: hasUnread = false } = useQuery({
    queryKey: ['unread-notifications-badge'],
    queryFn: () => notificationsApi.getMine(true, 0, 1),
    select: (res) => (res.data.data?.totalElements ?? 0) > 0,
    enabled: isAuthenticated,
    staleTime: 60_000,
    retry: false,
  });

  const isActive = (path: string) =>
    location.pathname.endsWith(path) || location.pathname === lp(path);

  return (
    <header className="header">
      <div className="header-inner container">

        {/* LEFT */}
        <div className="header-left">
          <Link to={lp('/')} className="logo">
            <span className="logo-mark">
              <span className="logo-mark__diamond" />
              <span className="logo-mark__pin" />
            </span>
            <span className="logo-text">
              Top<span>dim</span>
            </span>
          </Link>

          <nav className="nav desktop-nav">
            <Link to={lp('/')} className={`nav-link ${isActive('/') ? 'nav-link--active' : ''}`}>
              <Ticket size={18} />
              {t('nav.coupons')}
            </Link>
            {BAZAAR_NAV_ENABLED && (
              <Link to={lp('/bazaar')} className={`nav-link ${isActive('/bazaar') ? 'nav-link--active' : ''}`}>
                <MapPinned size={18} />
                {t('nav.bazaar')}
              </Link>
            )}
            {/* ✅ Заменён инлайн-стиль на класс .badge-wrapper */}
            <Link to={lp('/favorites')} className={`nav-link ${isActive('/favorites') ? 'nav-link--active' : ''}`}>
              <span className="badge-wrapper">
                <Heart size={18} />
                {favoriteIds.length > 0 && (
                  <span className="cart-badge">{favoriteIds.length}</span>
                )}
              </span>
              {t('nav.favorites')}
            </Link>
          </nav>
        </div>

        {/* RIGHT */}
        <div className="header-actions">
          <div className="header-actions-desktop">
            <Link to={lp('/search')} className="icon-button">
              <Search size={19} />
            </Link>

            <button className="icon-button" onClick={toggleCart}>
              <ShoppingBag size={19} />
              {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
            </button>

            <LanguageSelector />

            <Link to={lp(isAuthenticated ? '/profile?tab=notifications' : '/login')} className="account-pill">
              <span className="badge-wrapper">
                <User size={18} />
                {hasUnread && <span className="notification-dot" />}
              </span>
              <span>{isAuthenticated ? user?.firstName ?? t('header.profile') : t('header.login')}</span>
            </Link>
          </div>

          {/* BURGER */}
          <button
            className="mobile-menu-btn"
            onClick={() => setMobileMenuOpen(v => !v)}
          >
            {mobileMenuOpen ? <X size={28} strokeWidth={2} /> : <Menu size={28} strokeWidth={2} />}
          </button>
        </div>
      </div>

      {/* MOBILE MENU */}
      <div className={`mobile-menu ${mobileMenuOpen ? 'open' : ''}`}>
        <Link
          to={lp('/')}
          className="mobile-menu-action"
          onClick={() => setMobileMenuOpen(false)}
        >
          <Ticket size={MOBILE_MENU_ICON} />
          <span>{t('nav.coupons')}</span>
        </Link>

        <Link
          to={lp('/favorites')}
          className="mobile-menu-action"
          onClick={() => setMobileMenuOpen(false)}
        >
          <span className="badge-wrapper">
            <Heart size={MOBILE_MENU_ICON} />
            {favoriteIds.length > 0 && (
              <span className="cart-badge">{favoriteIds.length}</span>
            )}
          </span>
          <span>{t('nav.favorites')}</span>
        </Link>

        <Link
          to={lp('/search')}
          className="mobile-menu-action"
          onClick={() => setMobileMenuOpen(false)}
        >
          <Search size={MOBILE_MENU_ICON} />
          <span>{t('common.search')}</span>
        </Link>

        <button
          type="button"
          className="mobile-menu-action"
          onClick={() => {
            toggleCart();
            setMobileMenuOpen(false);
          }}
        >
          <span className="badge-wrapper">
            <ShoppingBag size={MOBILE_MENU_ICON} />
            {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
          </span>
          <span>{t('cart.title')}</span>
        </button>

        <Link
          to={lp(isAuthenticated ? '/profile?tab=notifications' : '/login')}
          className="mobile-menu-action"
          onClick={() => setMobileMenuOpen(false)}
        >
          <span className="badge-wrapper">
            <User size={MOBILE_MENU_ICON} />
            {hasUnread && <span className="notification-dot" />}
          </span>
          <span>{isAuthenticated ? user?.firstName ?? t('header.profile') : t('header.profile')}</span>
        </Link>

        <div className="mobile-menu-tools">
          <div className="mobile-menu-tool-item">
            <LanguageSelector />
          </div>
        </div>
      </div>
    </header>
  );
}