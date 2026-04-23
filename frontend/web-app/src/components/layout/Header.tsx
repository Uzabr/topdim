import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Heart, MapPinned, Menu, Search, ShoppingBag, Ticket, User, X } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import { useFavoritesStore } from '../../store/favoritesStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import LanguageSelector from '../ui/LanguageSelector';
import './Header.css';

export default function Header() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const { totalItems, toggleCart } = useCartStore();
  const { favoriteIds } = useFavoritesStore();
  const { isAuthenticated, user } = useAuthStore();
  const { t } = useTranslation();
  const lp = useLocalePath();

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
            <Link to={lp('/bazaar')} className={`nav-link ${isActive('/bazaar') ? 'nav-link--active' : ''}`}>
              <MapPinned size={18} />
              {t('nav.bazaar')}
            </Link>
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
          <Link to={lp('/search')} className="icon-button">
            <Search size={19} />
          </Link>

          <button className="icon-button" onClick={toggleCart}>
            <ShoppingBag size={19} />
            {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
          </button>

          <LanguageSelector />

          <Link to={lp(isAuthenticated ? '/profile' : '/login')} className="account-pill">
            <User size={18} />
            <span>{isAuthenticated ? user?.firstName ?? 'Profile' : t('header.login')}</span>
          </Link>

          {/* BURGER */}
          <button
            className="mobile-menu-btn"
            onClick={() => setMobileMenuOpen(v => !v)}
          >
            {mobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
      </div>

      {/* MOBILE MENU */}
      <div className={`mobile-menu ${mobileMenuOpen ? 'open' : ''}`}>
        <Link to={lp('/')} onClick={() => setMobileMenuOpen(false)}>
          {t('nav.coupons')}
        </Link>
        <Link to={lp('/bazaar')} onClick={() => setMobileMenuOpen(false)}>
          {t('nav.bazaar')}
        </Link>
        <Link to={lp('/favorites')} onClick={() => setMobileMenuOpen(false)}>
          {t('nav.favorites')}
        </Link>
      </div>
    </header>
  );
}