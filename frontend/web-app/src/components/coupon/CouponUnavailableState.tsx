import { Link } from 'react-router-dom';
import { ArrowLeft, SearchX } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../../hooks/useLocalePath';

export default function CouponUnavailableState() {
  const { t } = useTranslation();
  const lp = useLocalePath();

  return (
    <div className="coupon-unavailable">
      <div className="coupon-unavailable__card">
        <div className="coupon-unavailable__icon">
          <SearchX size={34} />
        </div>
        <h1>{t('couponDetail.unavailableTitle')}</h1>
        <p>{t('couponDetail.unavailableDesc')}</p>
        <div className="coupon-unavailable__actions">
          <Link to={lp('/coupons')} className="coupon-unavailable__primary">
            {t('couponDetail.unavailableBrowse')}
          </Link>
          <Link to={lp('/')} className="coupon-unavailable__secondary">
            <ArrowLeft size={16} />
            {t('notFound.home')}
          </Link>
        </div>
      </div>
    </div>
  );
}
