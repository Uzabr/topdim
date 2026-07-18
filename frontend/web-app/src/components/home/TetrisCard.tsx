import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { CouponOffer } from '../../api/coupons';
import FavoriteButton from '../ui/FavoriteButton';
import { useLocalePath } from '../../hooks/useLocalePath';
import { calcDiscount } from '../../utils/format';
import './TetrisCard.css';

/**
 * Форма карточки в мозаике. Уголок одной встаёт в вырез соседней, поэтому
 * формы жёстко привязаны к позиции в блоке (design_handoff_sizbiz → §10.5a).
 */
export type TetrisShape = 'tall' | 'l-down' | 'l-left' | 'l-right' | 'square';

interface TetrisCardProps {
  coupon: CouponOffer;
  shape: TetrisShape;
}

/** Район из адреса: «Яккасарай, ул. Шота Руставели 21» → «Яккасарай». */
function district(address?: string): string | undefined {
  return address?.split(',')[0]?.trim() || undefined;
}

/**
 * Г-образная карточка ленты: длинная часть — фото, уголок — цена и данные.
 * Части лежат отдельными прямоугольниками одного цвета и заходят друг на друга,
 * поэтому шва между ними не видно.
 */
export default function TetrisCard({ coupon, shape }: TetrisCardProps) {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const currency = t('common.currency.sum');

  const discount = coupon.discountPercent || calcDiscount(coupon.oldPrice ?? 0, coupon.fromPrice);
  const to = lp(`/coupons/${coupon.id}`);

  const meta = [
    district(coupon.merchant?.primaryLocation?.address),
    coupon.averageRating ? `★ ${coupon.averageRating.toFixed(1).replace('.', ',')}` : undefined,
    coupon.totalSold
      ? t('couponCard.purchases', { count: coupon.totalSold.toLocaleString(locale) })
      : undefined,
  ].filter(Boolean);

  return (
    <article className={`tcard tcard--${shape}`}>
      <Link to={to} className="tcard__photo" aria-label={coupon.title}>
        <span className="tcard__frame">
          {coupon.coverImageUrl ? (
            <img src={coupon.coverImageUrl} alt="" loading="lazy" />
          ) : (
            <span className="tcard__fallback">{coupon.merchant?.name}</span>
          )}

          {discount > 0 && (
            <span className="tcard__discount">
              {t('couponCard.discountUpTo', { percent: discount })}
            </span>
          )}
        </span>

        <FavoriteButton couponId={coupon.id} />
      </Link>

      <Link to={to} className="tcard__info">
        <span className="tcard__pricing">
          <span className="tcard__price">
            {t('couponCard.priceFrom', {
              price: coupon.fromPrice.toLocaleString(locale),
              currency,
            })}
          </span>
          {coupon.oldPrice && (
            <span className="tcard__old">{coupon.oldPrice.toLocaleString(locale)}</span>
          )}
        </span>

        <h3 className="tcard__title">{coupon.title}</h3>

        {meta.length > 0 && <p className="tcard__meta">{meta.join(' · ')}</p>}
      </Link>
    </article>
  );
}
