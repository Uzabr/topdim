import { Heart } from 'lucide-react';
import { useFavoritesStore } from '../../store/favoritesStore';
import './FavoriteButton.css';

interface FavoriteButtonProps {
  couponId: number;
  isTop?: boolean;
}

export default function FavoriteButton({ couponId, isTop = false }: FavoriteButtonProps) {
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
      aria-label="В избранное"
    >
      <Heart size={16} fill={fav ? 'currentColor' : 'none'} />
      {isTop && <span className="favorite-button__dot" title="Топ акция"></span>}
    </button>
  );
}
