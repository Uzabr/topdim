import { Link } from 'react-router-dom';
import { Clock, TrendingUp, Gift } from 'lucide-react';
import type { CouponOffer } from '../../api/coupons';
import './CouponCard.css';

interface CouponCardProps {
  coupon: CouponOffer;
}

export default function CouponCard({ coupon }: CouponCardProps) {
  const discount = coupon.discountPercent || (coupon.oldPrice
    ? Math.round((1 - coupon.fromPrice / coupon.oldPrice) * 100)
    : 0);

  const daysLeft = coupon.buyUntil
    ? Math.max(0, Math.ceil((new Date(coupon.buyUntil).getTime() - Date.now()) / 86400000))
    : null;

  return (
    <Link to={`/coupons/${coupon.id}`} className="coupon-card">
      <div className="coupon-card__image">
        {coupon.coverImageUrl ? (
          <img src={coupon.coverImageUrl} alt={coupon.title} loading="lazy" />
        ) : (
          <div className="coupon-card__placeholder">💎</div>
        )}
        {discount > 0 && (
          <span className="coupon-card__discount">-{discount}%</span>
        )}
        {coupon.giftAvailable && (
          <span className="coupon-card__gift"><Gift size={14} /></span>
        )}
      </div>

      <div className="coupon-card__body">
        <div className="coupon-card__merchant">{coupon.merchant.name}</div>
        <h3 className="coupon-card__title">{coupon.title}</h3>

        {coupon.shortDescription && (
          <p className="coupon-card__desc">{coupon.shortDescription}</p>
        )}

        <div className="coupon-card__pricing">
          {coupon.oldPrice && (
            <span className="coupon-card__old-price">
              {coupon.oldPrice.toLocaleString()} сум
            </span>
          )}
          <span className="coupon-card__price">
            от {coupon.fromPrice.toLocaleString()} сум
          </span>
        </div>

        <div className="coupon-card__footer">
          <span className="coupon-card__sold">
            <TrendingUp size={14} />
            {coupon.totalSold} продано
          </span>
          {daysLeft !== null && daysLeft <= 7 && (
            <span className="coupon-card__time">
              <Clock size={14} />
              {daysLeft === 0 ? 'Сегодня' : `${daysLeft} дн.`}
            </span>
          )}
        </div>
      </div>
    </Link>
  );
}
