import type { CouponOption } from '../../api/coupons';
import { formatPrice } from '../../utils/format';
import { Users, CreditCard, ShoppingCart } from 'lucide-react';
import { useTranslation } from 'react-i18next';

interface CouponVariantCardProps {
  option: CouponOption;
  onBuy: (option: CouponOption) => void;
  onAddToCart: (option: CouponOption) => void;
}

export default function CouponVariantCard({ option, onBuy, onAddToCart }: CouponVariantCardProps) {
  const { t, i18n } = useTranslation();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';
  const remaining = option.quantityLimit ? option.quantityLimit - option.quantitySold : null;
  const soldPct = option.quantityLimit ? (option.quantitySold / option.quantityLimit) * 100 : 0;

  return (
    <div className="detail-option">
      <div className="detail-option__info">
        <h3>{option.title}</h3>
        <span className="detail-option__bought">
          <Users size={14} />
          {t('couponDetail.boughtCount', { count: option.quantitySold.toLocaleString(locale) })}
        </span>
        <div className="detail-option__pricing">
          <span className="detail-option__price">{formatPrice(option.couponPrice)}</span>
          {option.regularPrice !== option.couponPrice && (
            <span className="detail-option__old">{formatPrice(option.regularPrice)}</span>
          )}
        </div>
        {remaining !== null && (
          <div className="detail-option__stock">
            <div className="detail-option__progress">
              <div className="detail-option__progress-fill" style={{ width: `${soldPct}%` }} />
            </div>
            <span>{t('couponDetail.remaining', { count: remaining })}</span>
          </div>
        )}
      </div>
      <div className="detail-option__buttons">
        <button className="detail-option__btn detail-option__btn--buy" onClick={() => onBuy(option)}>
          <CreditCard size={15} />
          {t('couponDetail.buy')}
        </button>
        <button className="detail-option__btn detail-option__btn--cart" onClick={() => onAddToCart(option)}>
          <ShoppingCart size={15} />
          {t('couponDetail.addToCart')}
        </button>
      </div>
    </div>
  );
}
