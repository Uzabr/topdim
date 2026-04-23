import { Clock3, Flame, Star, Ticket, Users, Heart } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useFavoritesStore } from '../../store/favoritesStore';
import type { TopdimDeal } from '../../data/topdim';
import { deriveCouponPreview } from '../../utils/couponPreview';
import './DealCard.css';

interface DealCardProps {
  deal: TopdimDeal;
  layout?: 'featured' | 'compact' | 'standard';
}

export default function DealCard({ deal, layout = 'standard' }: DealCardProps) {
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const fav = isFavorite(deal.id);

  return (
    <Link to={`/coupons/${deal.id}`} className={`deal-card deal-card--${layout}`}>
      <div className="deal-card__media">
        <img src={deal.image} alt={deal.title} loading="lazy" />
        <div className="deal-card__overlay" />
        <button 
          className={`deal-card__favorite ${fav ? 'deal-card__favorite--active' : ''}`}
          onClick={(e) => { e.preventDefault(); toggleFavorite(deal.id); }}
        >
          <Heart size={18} fill={fav ? "currentColor" : "none"} />
        </button>
        <span className="deal-card__discount">-{deal.discountPercent}%</span>
        {deal.isHot && (
          <span className="deal-card__hot">
            <Flame size={14} />
            Горит
          </span>
        )}
        <div className="deal-card__timer">
          <Clock3 size={14} />
          {deal.countdownText}
        </div>
      </div>

      <div className="deal-card__body">
        <div className="deal-card__meta">
          <span>{deal.merchant.name}</span>
          <span>{deal.location}</span>
        </div>
        <h3>{deal.title}</h3>
        <p>{deriveCouponPreview(deal.offerDescription)}</p>

        <div className="deal-card__tags">
          <span>{deal.vibe}</span>
          <span>
            <Star size={14} fill="currentColor" />
            {deal.rating} ({deal.reviews})
          </span>
        </div>

        <div className="deal-card__pricing">
          <div>
            <strong>{deal.fromPrice.toLocaleString()} сум</strong>
            {deal.oldPrice && <span>{deal.oldPrice.toLocaleString()} сум</span>}
          </div>
          <button type="button">
            <Ticket size={16} />
            Забрать
          </button>
        </div>

        <div className="deal-card__proof">
          <span>{deal.stockLeft} купонов осталось</span>
          <span>
            <Users size={14} />
            {deal.boughtToday} купили сегодня
          </span>
        </div>
      </div>
    </Link>
  );
}
