import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { MapPin, Store, Clock } from 'lucide-react';
import type { Bazaar } from '../../api/bazaars';
import { useLocalePath } from '../../hooks/useLocalePath';
import './DirectoryCards.css';

interface Props {
  bazaar: Bazaar;
  compact?: boolean;
}

export default function DirectoryBazaarCard({ bazaar, compact = false }: Props) {
  const { t } = useTranslation();
  const lp = useLocalePath();
  const typeLabels: Record<string, string> = {
    BAZAAR: t('directory.types.bazaar'),
    SHOPPING_CENTER: t('directory.types.shoppingCenter'),
    MARKET: t('directory.types.market'),
    TRADE_COMPLEX: t('directory.types.tradeComplexFull'),
  };
  return (
    <Link to={lp(`/bazaar/${bazaar.id}`)} className={`dir-card dir-card--bazaar ${compact ? 'dir-card--compact' : ''}`}>
      {bazaar.coverImageUrl && !compact && (
        <div className="dir-card__img">
          <img src={bazaar.coverImageUrl} alt={bazaar.name} loading="lazy" />
        </div>
      )}
      <div className="dir-card__body">
        <div className="dir-card__badges">
          <span className="dir-card__type">{typeLabels[bazaar.type] || bazaar.type}</span>
          {bazaar.shopCount > 0 && (
            <span className="dir-card__shop-count">
              <Store size={12} /> {bazaar.shopCount} магазинов
            </span>
          )}
        </div>
        <h3 className="dir-card__title">{bazaar.name}</h3>
        {bazaar.nameUz && <p className="dir-card__subtitle">{bazaar.nameUz}</p>}
        {!compact && bazaar.description && (
          <p className="dir-card__desc">{bazaar.description.slice(0, 120)}{bazaar.description.length > 120 ? '…' : ''}</p>
        )}
        <div className="dir-card__meta">
          {bazaar.address && (
            <span><MapPin size={14} />{bazaar.address}</span>
          )}
          {bazaar.workingHours && (
            <span><Clock size={14} />{bazaar.workingHours}</span>
          )}
        </div>
      </div>
    </Link>
  );
}
