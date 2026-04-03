import { Clock3, Flame, Star, Ticket, Users } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { TopdimDeal } from '../../data/topdim';
import './DealCard.css';

interface DealCardProps {
  deal: TopdimDeal;
  layout?: 'featured' | 'compact' | 'standard';
}

export default function DealCard({ deal, layout = 'standard' }: DealCardProps) {
  return (
    <Link to={`/coupons/${deal.id}`} className={`deal-card deal-card--${layout}`}>
      <div className="deal-card__media">
        <img src={deal.image} alt={deal.title} loading="lazy" />
        <div className="deal-card__overlay" />
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
        <p>{deal.shortDescription}</p>

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
