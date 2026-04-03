import { Grid2X2, MapPin, Route, Star } from 'lucide-react';
import type { BazaarItem } from '../../data/topdim';
import './BazaarCard.css';

interface BazaarCardProps {
  item: BazaarItem;
  viewMode: 'grid' | 'list';
}

export default function BazaarCard({ item, viewMode }: BazaarCardProps) {
  return (
    <article className={`bazaar-item-card bazaar-item-card--${viewMode}`}>
      <div className="bazaar-item-card__image">
        <img src={item.image} alt={item.title} loading="lazy" />
        <span className="bazaar-item-card__discount">{item.discountLabel}</span>
      </div>
      <div className="bazaar-item-card__content">
        <div className="bazaar-item-card__topline">
          <span>{item.badge}</span>
          <span>
            <Star size={14} fill="currentColor" />
            4.8
          </span>
        </div>
        <h3>{item.title}</h3>
        <div className="bazaar-item-card__meta">
          <span>
            <Grid2X2 size={15} />
            {item.bazaarName}
          </span>
          <span>
            <Route size={15} />
            {item.distanceKm} км
          </span>
          <span>
            <MapPin size={15} />
            {item.location}
          </span>
        </div>
        <div className="bazaar-item-card__footer">
          <div>
            <strong>{item.price.toLocaleString()} сум</strong>
            {item.oldPrice && <span>{item.oldPrice.toLocaleString()} сум</span>}
          </div>
          <button type="button">Смотреть</button>
        </div>
      </div>
    </article>
  );
}
