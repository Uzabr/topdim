import { useTranslation } from 'react-i18next';
import type { CouponOffer, CouponOption } from '../../api/coupons';
import { optionStock } from '../../utils/couponStock';
import { calcDiscount, formatDate, isPast } from '../../utils/format';
import './PurchasePanel.css';

interface PurchasePanelProps {
  coupon: CouponOffer;
  option: CouponOption;
  onBuy: () => void;
  onAddToCart: () => void;
}

/**
 * Чёрная карточка покупки (sticky). Считает всё по ВЫБРАННОЙ опции, а не по
 * купону: цена, скидка, остаток. Без таймера — срок показываем датами
 * (design_handoff_sizbiz → «Страница купона» → «Карточка покупки»).
 */
export default function PurchasePanel({ coupon, option, onBuy, onAddToCart }: PurchasePanelProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const currency = t('common.currency.sum');

  const discount = calcDiscount(option.regularPrice, option.couponPrice);
  const stock = optionStock(option);
  const soldOut = stock?.left === 0;
  const saleOver = isPast(coupon.buyUntil);
  const disabled = soldOut || saleOver;

  const ctaLabel = saleOver
    ? t('couponDetail.saleOver')
    : soldOut
      ? t('couponDetail.soldOut')
      : t('couponDetail.buy');

  return (
    <div className="buy">
      <div className="buy__card">
        {discount > 0 && <span className="buy__discount">−{discount}%</span>}

        <p className="buy__option">{option.title}</p>
        <div className="buy__prices">
          <span className="buy__price">
            {option.couponPrice.toLocaleString(locale)} {currency}
          </span>
          {option.regularPrice > option.couponPrice && (
            <span className="buy__old">{option.regularPrice.toLocaleString(locale)}</span>
          )}
        </div>
        {coupon.giftAvailable && <p className="buy__gift">{t('couponDetail.giftable')}</p>}

        <div className="buy__dates">
          <div className="buy__date">
            <span className="buy__date-label">{t('couponDetail.buyUntilLabel')}</span>
            <span className="buy__date-value">{formatDate(coupon.buyUntil)}</span>
          </div>
          <div className="buy__date">
            <span className="buy__date-label">{t('couponDetail.useUntilLabel')}</span>
            <span className="buy__date-value">{formatDate(coupon.useUntil)}</span>
          </div>
        </div>

        <button
          type="button"
          className={`buy__cta${disabled ? ' buy__cta--disabled' : ''}`}
          onClick={onBuy}
          disabled={disabled}
        >
          {ctaLabel}
        </button>

        {!disabled && (
          <button type="button" className="buy__secondary" onClick={onAddToCart}>
            {t('couponDetail.addToCart')}
          </button>
        )}

        {stock && (
          <div className="buy__progress">
            <div className="buy__bar">
              <span className="buy__bar-fill" style={{ width: `${stock.percent}%` }} />
            </div>
            <div className="buy__progress-meta">
              <span>{t('couponDetail.soldOf', { sold: stock.sold, limit: stock.limit })}</span>
              <span>{t('couponDetail.optionLeft', { count: stock.left })}</span>
            </div>
          </div>
        )}

        <div className="buy__chain">
          <span className="buy__chain-step">{t('couponDetail.chainBuy')}</span>
          <span className="buy__chain-arrow">→</span>
          <span className="buy__chain-step">{t('couponDetail.chainQr')}</span>
          <span className="buy__chain-arrow">→</span>
          <span className="buy__chain-step">{t('couponDetail.chainCome')}</span>
        </div>

        <p className="buy__refund">{t('couponDetail.refundNote')}</p>
      </div>

      <div className="buy__stats">
        <span className="buy__dot" />
        <span>
          {t('couponDetail.boughtViews', {
            bought: coupon.totalSold.toLocaleString(locale),
            views: coupon.viewCount.toLocaleString(locale),
          })}
        </span>
      </div>
    </div>
  );
}
