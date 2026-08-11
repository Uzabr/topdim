import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { CouponOffer } from '../../api/coupons';
import { useCountdown } from '../../hooks/useCountdown';
import { useLocalePath } from '../../hooks/useLocalePath';
import { couponStock } from '../../utils/couponStock';
import { calcDiscount } from '../../utils/format';
import { srcAt } from '../../utils/imageUrl';
import './HotTile.css';

interface HotTileProps {
  coupon: CouponOffer;
  /** Чем купон попал в лид: сортировкой по скидке или по популярности. */
  lead: 'discount' | 'popular';
}

/**
 * Горящий тайл — чёрный, на два ряда. Таймер и прогресс выкупа показываем только
 * когда они реальны: таймер — если до buyUntil меньше суток, прогресс — если у
 * опций есть лимит (design_handoff_sizbiz → §10.5).
 */
export default function HotTile({ coupon, lead }: HotTileProps) {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const currency = t('common.currency.sum');

  const countdown = useCountdown(coupon.buyUntil);
  const stock = couponStock(coupon);
  const discount = coupon.discountPercent || calcDiscount(coupon.oldPrice ?? 0, coupon.fromPrice);
  const urgent = Boolean(countdown?.isUrgent);

  return (
    <Link to={lp(`/coupons/${coupon.id}`)} className="hot-tile">
      <div className="hot-tile__head">
        <span className="hot-tile__label">
          {urgent
            ? t('home.feed.hotLabel')
            : lead === 'discount'
              ? t('home.feed.bestLabel')
              : t('home.feed.popularLabel')}
        </span>
        {urgent && countdown && <span className="hot-tile__timer">{countdown.clock}</span>}
      </div>

      <div className="hot-tile__media">
        {coupon.coverImageUrl ? (
          <img src={srcAt(coupon.coverImageUrl, 640)} alt={coupon.title} loading="lazy" />
        ) : (
          <span className="hot-tile__media-fallback">{coupon.merchant?.name}</span>
        )}
      </div>

      <div className="hot-tile__prices">
        <span className="hot-tile__discount">−{discount}%</span>
        <span className="hot-tile__price-col">
          <span className="hot-tile__price">
            {coupon.fromPrice.toLocaleString(locale)} {currency}
          </span>
          {coupon.oldPrice && (
            <span className="hot-tile__old">
              {coupon.oldPrice.toLocaleString(locale)} {currency}
            </span>
          )}
        </span>
      </div>

      <p className="hot-tile__title">{coupon.title}</p>

      {stock && (
        <div className="hot-tile__progress">
          <div className="hot-tile__bar">
            <span className="hot-tile__bar-fill" style={{ width: `${stock.percent}%` }} />
          </div>
          <div className="hot-tile__progress-meta">
            <span>{t('home.feed.soldPercent', { percent: stock.percent })}</span>
            <span>{t('home.feed.leftCount', { count: stock.left })}</span>
          </div>
        </div>
      )}
    </Link>
  );
}
