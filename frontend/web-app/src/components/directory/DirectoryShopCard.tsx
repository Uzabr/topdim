import { Link } from 'react-router-dom';
import { MapPin, Store, Tag } from 'lucide-react';
import type { Shop } from '../../api/bazaars';
import './DirectoryCards.css';

interface Props {
  shop: Shop;
  compact?: boolean;
}

export default function DirectoryShopCard({ shop, compact = false }: Props) {
  const CardWrapper: any = shop.isExternal ? 'a' : Link;
  const linkProps = shop.isExternal
    ? { href: `https://2gis.uz/search/${encodeURIComponent(shop.name)}`, target: '_blank', rel: 'noreferrer' }
    : { to: `/shops/${shop.id}` };

  return (
    <CardWrapper {...linkProps} className={`dir-card dir-card--shop ${compact ? 'dir-card--compact' : ''}`}>
      {shop.photos?.[0] && !compact && (
        <div className="dir-card__img">
          <img src={shop.photos[0]} alt={shop.name} loading="lazy" />
        </div>
      )}
      <div className="dir-card__body">
        <div className="dir-card__badges">
          {shop.isExternal && (
            <span className="dir-card__category" style={{ background: '#FF6660', color: 'white' }}>Из 2ГИС</span>
          )}
          {shop.category && (
            <span className="dir-card__category"><Tag size={12} /> {shop.category}</span>
          )}
        </div>
        <h3 className="dir-card__title">{shop.name}</h3>
        {!compact && shop.goodsDescription && (
          <p className="dir-card__desc">{shop.goodsDescription.slice(0, 100)}{shop.goodsDescription.length > 100 ? '…' : ''}</p>
        )}
        <div className="dir-card__meta">
          {shop.locationType === 'BAZAAR' && shop.bazaar && (
            <span><Store size={14} />{shop.bazaar.name}
              {shop.rowNumber ? `, ряд ${shop.rowNumber}` : ''}
              {shop.shopNumber ? `, место ${shop.shopNumber}` : ''}
            </span>
          )}
          {shop.locationType === 'STANDALONE' && shop.address && (
            <span><MapPin size={14} />{shop.address}</span>
          )}
        </div>
      </div>
    </CardWrapper>
  );
}
