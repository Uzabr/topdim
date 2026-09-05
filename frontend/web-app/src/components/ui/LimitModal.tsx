import { Link } from 'react-router-dom';
import { Heart } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useFavoritesStore } from '../../store/favoritesStore';
import { useAuthStore } from '../../store/authStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import { useScrollLock } from '../../hooks/useScrollLock';
import './LimitModal.css';

export default function LimitModal() {
  const { t } = useTranslation();
  const { showLimitModal, limitMessage, closeLimitModal } = useFavoritesStore();
  const { isAuthenticated } = useAuthStore();
  const lp = useLocalePath();
  useScrollLock(showLimitModal);

  if (!showLimitModal) return null;

  return (
    <div className="limit-modal-overlay" onClick={closeLimitModal}>
      <div className="limit-modal" onClick={(e) => e.stopPropagation()}>
        <Heart className="limit-modal__icon" size={48} strokeWidth={1.5} />
        <h3 className="limit-modal__title">{t('favorites.limitTitle')}</h3>
        <p className="limit-modal__text">{limitMessage}</p>
        <button className="limit-modal__btn" onClick={closeLimitModal}>
          {t('favorites.limitOk')}
        </button>
        {!isAuthenticated && (
          <Link to={lp('/login')} className="limit-modal__login-link" onClick={closeLimitModal}>
            {t('favorites.limitLogin')}
          </Link>
        )}
      </div>
    </div>
  );
}
