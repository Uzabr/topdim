import { useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { Ticket, Map, ShoppingCart, User, Search, Menu, X } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import './Header.css';

export default function Header() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const location = useLocation();
  const { isAuthenticated, user } = useAuthStore();
  const { totalItems, toggleCart } = useCartStore();

  const isActive = (path: string) => location.pathname === path;

  return (
    <header className="header glass">
      <div className="header-inner container">
        <Link to="/" className="logo">
          <span className="logo-icon">💎</span>
          <span className="logo-text">Top<span className="text-gradient">Dim</span></span>
        </Link>

        <nav className={`nav ${mobileMenuOpen ? 'nav--open' : ''}`}>
          <Link
            to="/"
            className={`nav-link ${isActive('/') ? 'nav-link--active' : ''}`}
            onClick={() => setMobileMenuOpen(false)}
          >
            <Ticket size={18} />
            <span>Купоны</span>
          </Link>
          <Link
            to="/bazaar"
            className={`nav-link ${isActive('/bazaar') ? 'nav-link--active' : ''}`}
            onClick={() => setMobileMenuOpen(false)}
          >
            <Map size={18} />
            <span>Базар</span>
          </Link>
          <Link
            to="/search"
            className={`nav-link ${isActive('/search') ? 'nav-link--active' : ''}`}
            onClick={() => setMobileMenuOpen(false)}
          >
            <Search size={18} />
            <span>Поиск</span>
          </Link>
        </nav>

        <div className="header-actions">
          <button className="cart-btn" onClick={toggleCart} aria-label="Корзина">
            <ShoppingCart size={20} />
            {totalItems > 0 && <span className="cart-badge">{totalItems}</span>}
          </button>

          {isAuthenticated ? (
            <Link to="/profile" className="profile-btn">
              <User size={20} />
              <span className="profile-name">{user?.firstName}</span>
            </Link>
          ) : (
            <Link to="/login" className="login-btn">
              Войти
            </Link>
          )}

          <button
            className="mobile-menu-btn"
            onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
            aria-label="Меню"
          >
            {mobileMenuOpen ? <X size={24} /> : <Menu size={24} />}
          </button>
        </div>
      </div>
    </header>
  );
}
