import type { CouponOption, CouponOffer } from '../../api/coupons';
import CouponVariantCard from './CouponVariantCard';
import { forwardRef } from 'react';
import { useTranslation } from 'react-i18next';

interface CouponVariantsSectionProps {
  coupon: CouponOffer;
  onBuy: (option: CouponOption) => void;
  onAddToCart: (option: CouponOption) => void;
}

const CouponVariantsSection = forwardRef<HTMLDivElement, CouponVariantsSectionProps>(
  ({ coupon, onBuy, onAddToCart }, ref) => {
    const { t } = useTranslation();
    const c = coupon;
    const displayOptions: CouponOption[] = c.options && c.options.length > 0
      ? c.options
      : [{
          id: 0,
          title: c.title,
          regularPrice: c.oldPrice || c.fromPrice,
          couponPrice: c.fromPrice,
          quantityLimit: 0,
          quantitySold: c.totalSold || 0,
          status: 'ACTIVE',
        }];

    return (
      <div className="detail-options" ref={ref}>
        <h2 className="detail-section-title">{t('couponDetail.variantsTitle')}</h2>
        {displayOptions.map((opt) => (
          <CouponVariantCard key={opt.id} option={opt} onBuy={onBuy} onAddToCart={onAddToCart} />
        ))}
      </div>
    );
  }
);

CouponVariantsSection.displayName = 'CouponVariantsSection';
export default CouponVariantsSection;
