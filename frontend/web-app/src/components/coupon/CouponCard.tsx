import { Link } from 'react-router-dom';
import { Clock3, Flame, Heart, MapPin, Star, Ticket, Users } from 'lucide-react';
import { useFavoritesStore } from '../../store/favoritesStore';
import './CouponCard.css';

export interface CouponCardData {
  id: number;
  title: string;
  shortDescription?: string;
  merchant: { id: number; name: string; logoUrl?: string };
  category?: { id: number; name: string; slug: string };
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  totalSold?: number;
  rating?: number;
  reviewCount?: number;
  address?: string;
  location?: string;
  isHot?: boolean;
  countdownText?: string;
  giftAvailable?: boolean;
}

interface CouponCardProps {
  coupon: CouponCardData;
  layout?: 'card' | 'carousel' | 'featured';
}

export default function CouponCard({ coupon, layout = 'card' }: CouponCardProps) {
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const fav = isFavorite(coupon.id);

  const discount =
    coupon.discountPercent ||
    (coupon.oldPrice
      ? Math.round((1 - coupon.fromPrice / coupon.oldPrice) * 100)
      : 0);

  const locationText = coupon.location || coupon.address || '';

  const formatSold = (n?: number) => {
    if (!n) return '';
    if (n >= 1000) return `${(n / 1000).toFixed(1).replace('.0', '')}k`;
    return String(n);
  };

  return (
    <Link to={`/coupons/${coupon.id}`} className={`coupon-card coupon-card--${layout}`}>
      {/* Image */}
      <div className="coupon-card__media">
        {coupon.coverImageUrl ? (
          <img src={coupon.coverImageUrl} alt={coupon.title} loading="lazy" />
        ) : (
          <div className="coupon-card__img-placeholder">
            <span>{coupon.merchant?.name?.charAt(0) || '💎'}</span>
          </div>
        )}

        <div className="coupon-card__overlay" />

        {/* Location badge on image */}
        {locationText && (
          <span className="coupon-card__location">
            <MapPin size={12} />
            {locationText}
          </span>
        )}

        {/* Discount badge */}
        {discount > 0 && (
          <span className="coupon-card__discount">до -{discount}%</span>
        )}

        {/* Hot badge */}
        {coupon.isHot && (
          <span className="coupon-card__hot">
            <Flame size={12} />
          </span>
        )}

        {/* Favorite button */}
        <button
          className={`coupon-card__fav ${fav ? 'coupon-card__fav--active' : ''}`}
          onClick={(e) => {
            e.preventDefault();
            e.stopPropagation();
            toggleFavorite(coupon.id);
          }}
          aria-label="В избранное"
        >
          <Heart size={16} fill={fav ? 'currentColor' : 'none'} />
        </button>

        {/* Countdown */}
        {coupon.countdownText && (
          <span className="coupon-card__timer">
            <Clock3 size={12} />
            {coupon.countdownText}
          </span>
        )}
      </div>

      {/* Body */}
      <div className="coupon-card__body">
        <div className="coupon-card__header">
          <span className="coupon-card__merchant">{coupon.merchant?.name}</span>
          {coupon.totalSold !== undefined && coupon.totalSold > 0 && (
            <span className="coupon-card__sold">
              <Users size={12} />
              {coupon.totalSold.toLocaleString('ru-RU')} покупок
            </span>
          )}
        </div>

        <h3 className="coupon-card__title">{coupon.title}</h3>

        {coupon.shortDescription && layout !== 'carousel' && (
          <p className="coupon-card__desc">{coupon.shortDescription}</p>
        )}

        {/* Rating */}
        {coupon.rating !== undefined && coupon.rating > 0 && (
          <div className="coupon-card__rating">
            <Star size={13} fill="currentColor" className="coupon-card__star" />
            <span className="coupon-card__rating-value">{coupon.rating.toFixed(1)}</span>
            {coupon.reviewCount !== undefined && (
              <span className="coupon-card__review-count">/{coupon.reviewCount} отзывов</span>
            )}
          </div>
        )}

        {/* Price */}
        <div className="coupon-card__pricing">
          <span className="coupon-card__price-pill">
            от {coupon.fromPrice.toLocaleString('ru-RU')} сум
          </span>
          {coupon.oldPrice && (
            <span className="coupon-card__old-price">
              {coupon.oldPrice.toLocaleString('ru-RU')} сум
            </span>
          )}
        </div>
      </div>
    </Link>
  );
}
