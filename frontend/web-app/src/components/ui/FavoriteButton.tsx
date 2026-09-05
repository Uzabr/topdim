import { useRef } from 'react';
import type { MouseEvent } from 'react';
import { Heart } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useFavoritesStore } from '../../store/favoritesStore';
import { flyFunnel } from '../../utils/funnel';
import './FavoriteButton.css';

interface FavoriteButtonProps {
  couponId: number;
}

export default function FavoriteButton({ couponId }: FavoriteButtonProps) {
  const { t } = useTranslation();
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const ref = useRef<HTMLButtonElement>(null);
  const fav = isFavorite(couponId);

  const click = (e: MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();

    // Снятие — сразу; добавление — «воронкой»: карточку затягивает в сердце шапки.
    if (fav) {
      toggleFavorite(couponId);
      return;
    }

    // Карточка ленты бывает обычной и Г-образной (мозаика) — воронка нужна обеим.
    const card = ref.current?.closest('.coupon-card, .tcard') ?? null;
    flyFunnel(card, 'fav-btn', () => toggleFavorite(couponId));
  };

  return (
    <button
      ref={ref}
      className={`favorite-button ${fav ? 'favorite-button--active' : ''}`}
      onClick={click}
      aria-label={t('couponDetail.favorite')}
      aria-pressed={fav}
    >
      <Heart size={20} fill={fav ? 'currentColor' : 'none'} />
    </button>
  );
}
