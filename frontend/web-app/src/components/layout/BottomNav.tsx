import { Heart, MapPinned, User, ShoppingBag, Home } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { useCartStore } from '../../store/cartStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import './BottomNav.css';

export default function BottomNav() {
  const location = useLocation();
  const { isAuthenticated } = useAuthStore();
  const { openCart, totalItems } = useCartStore();
  const lp = useLocalePath();
  
  const isActive = (path: string) => location.pathname.endsWith(path) || location.pathname === lp(path);

  return (
    <nav className="bottom-nav">
      <Link to={lp('/')} className={`bottom-nav-item ${isActive('/') ? 'active' : ''}`}>
        <div className="bottom-nav-icon-wrapper">
          <Home size={24} strokeWidth={isActive('/') ? 2.5 : 2} />
        </div>
        <span>Главная</span>
      </Link>
      
      <Link to={lp('/bazaar')} className={`bottom-nav-item ${isActive('/bazaar') ? 'active' : ''}`}>
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
      
      <Link to={lp('/favorites')} className={`bottom-nav-item ${isActive('/favorites') ? 'active' : ''}`}>
        <div className="bottom-nav-icon-wrapper">
          <Heart size={24} strokeWidth={isActive('/favorites') ? 2.5 : 2} />
        </div>
        <span>Избранное</span>
      </Link>
      
      <Link
        to={lp(isAuthenticated ? '/profile' : '/login')}
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
