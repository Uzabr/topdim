import { Heart, MapPinned, User, ShoppingBag, Home } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import './BottomNav.css';

export default function BottomNav() {
  const location = useLocation();
  const { isAuthenticated } = useAuthStore();
  const { openCart, totalItems } = useCartStore();
  
  const isActive = (path: string) => location.pathname === path;

  return (
    <nav className="bottom-nav">
      <Link to="/" className={`bottom-nav-item ${isActive('/') ? 'active' : ''}`}>
        <div className="bottom-nav-icon-wrapper">
          <Home size={24} strokeWidth={isActive('/') ? 2.5 : 2} />
        </div>
        <span>Главная</span>
      </Link>
      
      <Link to="/bazaar" className={`bottom-nav-item ${isActive('/bazaar') ? 'active' : ''}`}>
        <div className="bottom-nav-icon-wrapper">
          <MapPinned size={24} strokeWidth={isActive('/bazaar') ? 2.5 : 2} />
        </div>
        <span>Базар</span>
      </Link>
      
      <button 
        className="bottom-nav-item bottom-nav-item--cart" 
        onClick={openCart}
      >
        <div className="bottom-nav-icon-wrapper">
          <ShoppingBag size={24} strokeWidth={2} />
          {totalItems > 0 && <span className="bottom-nav-badge">{totalItems}</span>}
        </div>
        <span>Корзина</span>
      </button>
      
      <Link to="/favorites" className={`bottom-nav-item ${isActive('/favorites') ? 'active' : ''}`}>
        <div className="bottom-nav-icon-wrapper">
          <Heart size={24} strokeWidth={isActive('/favorites') ? 2.5 : 2} />
        </div>
        <span>Избранное</span>
      </Link>
      
      <Link
        to={isAuthenticated ? '/profile' : '/login'}
        className={`bottom-nav-item ${isActive('/profile') || isActive('/login') ? 'active' : ''}`}
      >
        <div className="bottom-nav-icon-wrapper">
          <User size={24} strokeWidth={isActive('/profile') || isActive('/login') ? 2.5 : 2} />
        </div>
        <span>Профиль</span>
      </Link>
    </nav>
  );
}
