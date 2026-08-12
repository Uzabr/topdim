import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import FavoriteButton from '../ui/FavoriteButton';
import { useLocalePath } from '../../hooks/useLocalePath';
import { srcAt } from '../../utils/imageUrl';
import './CouponCard.css';

export interface CouponCardData {
  id: number;
  title: string;
  /** Canonical offer text. shortDescription is derived from this if not set. */
  offerDescription?: string;
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

/** Район из адреса: «Яккасарай, ул. Шота Руставели 21» → «Яккасарай». */
function district(address?: string): string | undefined {
  return address?.split(',')[0]?.trim() || undefined;
}

/**
 * Карточка купона — «четыре кита» (design_handoff_sizbiz → §10.5):
 * скидка, цена «от N сум», район, покупки/рейтинг. Больше ничего — ни таймера,
 * ни описания: они только у горящего тайла и на странице купона.
 */
export default function CouponCard({ coupon, layout = 'card' }: CouponCardProps) {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const currency = t('common.currency.sum');

  const discount =
    coupon.discountPercent ||
    (coupon.oldPrice ? Math.round((1 - coupon.fromPrice / coupon.oldPrice) * 100) : 0);

  const meta = [
    district(coupon.address),
    coupon.rating ? `★ ${coupon.rating.toFixed(1)}` : undefined,
    coupon.totalSold
      ? t('couponCard.purchases', { count: coupon.totalSold.toLocaleString(locale) })
      : undefined,
  ].filter(Boolean);

  return (
    <Link to={lp(`/coupons/${coupon.id}`)} className={`coupon-card coupon-card--${layout}`}>
      <div className="coupon-card__media">
        {coupon.coverImageUrl ? (
          <img src={srcAt(coupon.coverImageUrl, 640)} alt={coupon.title} loading="lazy" />
        ) : (
          <div className="coupon-card__img-placeholder">
            <span>{coupon.merchant?.name?.charAt(0) || coupon.title.charAt(0)}</span>
          </div>
        )}

        <FavoriteButton couponId={coupon.id} />

        {discount > 0 && (
          <span className="coupon-card__discount">
            {t('couponCard.discountUpTo', { percent: discount })}
          </span>
        )}
      </div>

      <div className="coupon-card__body">
        <div className="coupon-card__pricing">
          <span className="coupon-card__price">
            {t('couponCard.priceFrom', { price: coupon.fromPrice.toLocaleString(locale), currency })}
          </span>
          {coupon.oldPrice && (
            <span className="coupon-card__old-price">{coupon.oldPrice.toLocaleString(locale)}</span>
          )}
        </div>

        <h3 className="coupon-card__title">{coupon.title}</h3>

        {meta.length > 0 && <p className="coupon-card__meta">{meta.join(' · ')}</p>}
      </div>
    </Link>
  );
}
