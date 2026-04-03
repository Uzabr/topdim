import { useState } from 'react';
import { Heart, MapPinned, Menu, Search, ShoppingBag, Ticket, User, X } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import './Header.css';

export default function Header() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const { totalItems, toggleCart } = useCartStore();
  const { isAuthenticated, user } = useAuthStore();

  const isActive = (path: string) => location.pathname === path;

  return (
    <header className="header glass">
      <div className="header-inner container">
        <Link to="/" className="logo" onClick={() => setMobileMenuOpen(false)}>
          <span className="logo-mark" aria-hidden="true">
            <span className="logo-mark__diamond" />
            <span className="logo-mark__pin" />
          </span>
          <span className="logo-text">
            Top<span>dim</span>
          </span>
        </Link>

        <nav className={`nav ${mobileMenuOpen ? 'nav--open' : ''}`}>
          <Link to="/" className={`nav-link ${isActive('/') ? 'nav-link--active' : ''}`} onClick={() => setMobileMenuOpen(false)}>
            <Ticket size={18} />
            Купоны
          </Link>
          <Link to="/bazaar" className={`nav-link ${isActive('/bazaar') ? 'nav-link--active' : ''}`} onClick={() => setMobileMenuOpen(false)}>
            <MapPinned size={18} />
            Базар
          </Link>
          <Link to="/favorites" className={`nav-link ${isActive('/favorites') ? 'nav-link--active' : ''}`} onClick={() => setMobileMenuOpen(false)}>
            <Heart size={18} />
            Избранное
          </Link>
        </nav>

        <div className="header-actions">
          <Link to="/search" className="icon-button" aria-label="Поиск">
            <Search size={19} />
          </Link>
          <button type="button" className="icon-button" onClick={toggleCart} aria-label="Корзина">
            <ShoppingBag size={19} />
            {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
          </button>
          <Link to={isAuthenticated ? '/profile' : '/login'} className="account-pill">
            <User size={18} />
            <span>{isAuthenticated ? user?.firstName ?? 'Профиль' : 'Войти'}</span>
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
