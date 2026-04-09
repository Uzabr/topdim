import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Heart, MapPinned, Menu, Search, ShoppingBag, Ticket, User, X } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import { useFavoritesStore } from '../../store/favoritesStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import CitySelector from '../ui/CitySelector';
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

  const isActive = (path: string) => location.pathname.endsWith(path) || location.pathname === lp(path);

  return (
    <header className="header glass">
      <div className="header-inner container">
        <Link to={lp('/')} className="logo" onClick={() => setMobileMenuOpen(false)}>
          <span className="logo-mark" aria-hidden="true">
            <span className="logo-mark__diamond" />
            <span className="logo-mark__pin" />
          </span>
          <span className="logo-text">
            Top<span>dim</span>
          </span>
        </Link>

        <CitySelector />

        <nav className={`nav ${mobileMenuOpen ? 'nav--open' : ''}`}>
          <Link to={lp('/')} className={`nav-link ${isActive('/') ? 'nav-link--active' : ''}`} onClick={() => setMobileMenuOpen(false)}>
            <Ticket size={18} />
            {t('nav.coupons')}
          </Link>
          <Link to={lp('/bazaar')} className={`nav-link ${isActive('/bazaar') ? 'nav-link--active' : ''}`} onClick={() => setMobileMenuOpen(false)}>
            <MapPinned size={18} />
            {t('nav.bazaar')}
          </Link>
          <Link to={lp('/favorites')} className={`nav-link ${isActive('/favorites') ? 'nav-link--active' : ''}`} onClick={() => setMobileMenuOpen(false)}>
            <div style={{ position: 'relative', display: 'flex', alignItems: 'center' }}>
              <Heart size={18} />
              {favoriteIds.length > 0 && (
                <span className="cart-badge" style={{ position: 'absolute', top: '-8px', right: '-12px' }}>{favoriteIds.length}</span>
              )}
            </div>
            {t('nav.favorites')}
          </Link>
        </nav>

        <div className="header-actions">
          <LanguageSelector />
          <Link to={lp('/search')} className="icon-button" aria-label="Поиск">
            <Search size={19} />
          </Link>
          <button type="button" className="icon-button" onClick={toggleCart} aria-label="Корзина">
            <ShoppingBag size={19} />
            {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
          </button>
          <Link to={lp(isAuthenticated ? '/profile' : '/login')} className="account-pill">
            <User size={18} />
            <span>{isAuthenticated ? user?.firstName ?? t('header.profile') : t('header.login')}</span>
          </Link>
          <button
            type="button"
            className="mobile-menu-btn"
            aria-label="Меню"
            onClick={() => setMobileMenuOpen((value) => !value)}
          >
            {mobileMenuOpen ? <X size={22} /> : <Menu size={22} />}
          </button>
        </div>
      </div>
    </header>
  );
}
