import { Star } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import './StarRating.css';

interface StarRatingProps {
  rating: number;
  reviewCount?: number;
  size?: number;
  showCount?: boolean;
  interactive?: boolean;
  onRate?: (rating: number) => void;
}

export default function StarRating({
  rating,
  reviewCount,
  size = 16,
  showCount = true,
  interactive = false,
  onRate,
}: StarRatingProps) {
  const { t } = useTranslation();
  const stars = Array.from({ length: 5 }, (_, i) => {
    const diff = rating - i;
    if (diff >= 0.75) return 'full';
    if (diff >= 0.25) return 'half';
    return 'empty';
  });

  return (
    <div className={`star-rating ${interactive ? 'star-rating--interactive' : ''}`}>
      <div className="star-rating__stars">
        {stars.map((type, i) => (
          <button
            key={i}
            type="button"
            className={`star-rating__star star-rating__star--${type}`}
            onClick={interactive && onRate ? () => onRate(i + 1) : undefined}
            tabIndex={interactive ? 0 : -1}
            aria-label={t('starRating.stars', { count: i + 1 })}
          >
            <Star
              size={size}
              fill={type === 'empty' ? 'none' : 'currentColor'}
              strokeWidth={type === 'empty' ? 1.8 : 0}
            />
            {type === 'half' && (
              <span className="star-rating__half-mask">
                <Star size={size} fill="none" strokeWidth={1.8} />
              </span>
            )}
          </button>
        ))}
      </div>
      {showCount && (
        <span className="star-rating__info">
          <span className="star-rating__value">{rating.toFixed(1)}</span>
          {reviewCount !== undefined && (
            <span className="star-rating__count"> {t('starRating.reviews', { count: reviewCount })}</span>
          )}
        </span>
      )}
    </div>
  );
}
