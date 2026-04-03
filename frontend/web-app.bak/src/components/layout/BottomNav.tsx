import { Link, useLocation } from 'react-router-dom';
import { Ticket, Map, Search, User, ShoppingCart } from 'lucide-react';
import { useCartStore } from '../../store/cartStore';
import { useAuthStore } from '../../store/authStore';
import './BottomNav.css';

export default function BottomNav() {
  const location = useLocation();
  const { totalItems, toggleCart } = useCartStore();
  const { isAuthenticated } = useAuthStore();
  const isActive = (path: string) => location.pathname === path;

  return (
    <nav className="bottom-nav glass">
      <Link to="/" className={`bottom-nav-item ${isActive('/') ? 'active' : ''}`}>
        <Ticket size={20} />
        <span>Купоны</span>
      </Link>
      <Link to="/bazaar" className={`bottom-nav-item ${isActive('/bazaar') ? 'active' : ''}`}>
        <Map size={20} />
        <span>Базар</span>
      </Link>
      <button className="bottom-nav-item bottom-nav-cart" onClick={toggleCart}>
        <ShoppingCart size={20} />
        {totalItems > 0 && <span className="bottom-cart-badge">{totalItems}</span>}
        <span>Корзина</span>
      </button>
      <Link to="/search" className={`bottom-nav-item ${isActive('/search') ? 'active' : ''}`}>
        <Search size={20} />
        <span>Поиск</span>
      </Link>
      <Link
        to={isAuthenticated ? '/profile' : '/login'}
        className={`bottom-nav-item ${isActive('/profile') || isActive('/login') ? 'active' : ''}`}
      >
        <User size={20} />
        <span>{isAuthenticated ? 'Профиль' : 'Войти'}</span>
      </Link>
    </nav>
  );
}
