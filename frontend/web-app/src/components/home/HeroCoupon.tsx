import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import type { CouponOffer } from '../../api/coupons';
import { useCountdown } from '../../hooks/useCountdown';
import { useLocalePath } from '../../hooks/useLocalePath';
import { couponStock } from '../../utils/couponStock';
import { calcDiscount, formatDate } from '../../utils/format';
import './HeroCoupon.css';

interface HeroCouponProps {
  coupon: CouponOffer;
}

/**
 * Хиро «Купон дня» — предложение с самой большой скидкой (design_handoff_sizbiz
 * → «Главная»). Таймер считает до реального buyUntil: HH:MM:SS, когда до конца
 * меньше суток, иначе дата. Так таймер не обещает срочности, которой нет.
 */
export default function HeroCoupon({ coupon }: HeroCouponProps) {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const currency = t('common.currency.sum');

  const countdown = useCountdown(coupon.buyUntil);
  const stock = couponStock(coupon);
  const discount = coupon.discountPercent || calcDiscount(coupon.oldPrice ?? 0, coupon.fromPrice);

  return (
    <section className="hero-coupon">
      <div className="hero-coupon__body">
        <div className="hero-coupon__badges">
          <span className="hero-coupon__label">{t('home.hero.label')}</span>
          {countdown && (
            <span className="hero-coupon__timer">
              {countdown.isUrgent
                ? t('home.hero.endsIn', { time: countdown.clock })
                : t('home.hero.endsOn', { date: formatDate(coupon.buyUntil) })}
            </span>
          )}
        </div>

        <h1 className="hero-coupon__title">{coupon.title}</h1>

        <div className="hero-coupon__prices">
          <span className="hero-coupon__price">
            {coupon.fromPrice.toLocaleString(locale)} {currency}
          </span>
          {coupon.oldPrice && (
            <span className="hero-coupon__old">
              {coupon.oldPrice.toLocaleString(locale)} {currency}
            </span>
          )}
          {discount > 0 && <span className="hero-coupon__discount">−{discount}%</span>}
        </div>

        <div className="hero-coupon__actions">
          <Link to={lp(`/coupons/${coupon.id}`)} className="hero-coupon__buy">
            {t('home.hero.buy')}
          </Link>
          {stock && stock.left > 0 && (
            <span className="hero-coupon__stock">
              {t('home.hero.left', { left: stock.left, total: stock.limit })}
            </span>
          )}
        </div>
      </div>

      <Link to={lp(`/coupons/${coupon.id}`)} className="hero-coupon__media">
        {coupon.coverImageUrl ? (
          <img src={coupon.coverImageUrl} alt={coupon.title} />
        ) : (
          <span className="hero-coupon__media-fallback">{coupon.merchant?.name}</span>
        )}
      </Link>
    </section>
  );
}
