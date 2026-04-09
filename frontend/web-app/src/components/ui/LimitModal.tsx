import { Link } from 'react-router-dom';
import { useFavoritesStore } from '../../store/favoritesStore';
import { useAuthStore } from '../../store/authStore';
import './LimitModal.css';

export default function LimitModal() {
  const { showLimitModal, limitMessage, closeLimitModal } = useFavoritesStore();
  const { isAuthenticated } = useAuthStore();

  if (!showLimitModal) return null;

  return (
    <div className="limit-modal-overlay" onClick={closeLimitModal}>
      <div className="limit-modal" onClick={(e) => e.stopPropagation()}>
        <span className="limit-modal__icon">💛</span>
        <h3 className="limit-modal__title">Лимит избранного</h3>
        <p className="limit-modal__text">{limitMessage}</p>
        <button className="limit-modal__btn" onClick={closeLimitModal}>
          Понятно
        </button>
        {!isAuthenticated && (
          <Link to="/login" className="limit-modal__login-link" onClick={closeLimitModal}>
            Войти в аккаунт →
          </Link>
        )}
      </div>
    </div>
  );
}
