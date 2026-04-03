import { Heart, MapPinned, Ticket, User } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import './BottomNav.css';

export default function BottomNav() {
  const location = useLocation();
  const { isAuthenticated } = useAuthStore();
  const isActive = (path: string) => location.pathname === path;

  return (
    <nav className="bottom-nav glass">
      <Link to="/" className={`bottom-nav-item ${isActive('/') ? 'active' : ''}`}>
        <Ticket size={20} />
        <span>Купоны</span>
      </Link>
      <Link to="/bazaar" className={`bottom-nav-item ${isActive('/bazaar') ? 'active' : ''}`}>
        <MapPinned size={20} />
        <span>Базар</span>
      </Link>
      <Link to="/favorites" className={`bottom-nav-item ${isActive('/favorites') ? 'active' : ''}`}>
        <Heart size={20} />
        <span>Избранное</span>
      </Link>
      <Link
        to={isAuthenticated ? '/profile' : '/login'}
        className={`bottom-nav-item ${isActive('/profile') || isActive('/login') ? 'active' : ''}`}
      >
        <User size={20} />
        <span>Профиль</span>
      </Link>
    </nav>
  );
}
