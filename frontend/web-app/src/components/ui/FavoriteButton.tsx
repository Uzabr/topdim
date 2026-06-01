import { Heart } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useFavoritesStore } from '../../store/favoritesStore';
import './FavoriteButton.css';

interface FavoriteButtonProps {
  couponId: number;
}

export default function FavoriteButton({ couponId }: FavoriteButtonProps) {
  const { t } = useTranslation();
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const fav = isFavorite(couponId);

  return (
    <button
      className={`favorite-button ${fav ? 'favorite-button--active' : ''}`}
      onClick={(e) => {
        e.preventDefault();
        e.stopPropagation();
        toggleFavorite(couponId);
      }}
      aria-label={t('couponDetail.favorite')}
    >
      <Heart size={16} fill={fav ? 'currentColor' : 'none'} />
    </button>
  );
}
