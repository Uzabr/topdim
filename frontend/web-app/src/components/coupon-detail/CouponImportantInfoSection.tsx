import type { CouponOffer } from '../../api/coupons';
import { Calendar, Clock, MapPin, Info } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { formatDate } from '../../utils/format';

interface CouponImportantInfoSectionProps {
  coupon: CouponOffer;
}

export default function CouponImportantInfoSection({ coupon }: CouponImportantInfoSectionProps) {
  const { t } = useTranslation();
  const c = coupon;
  const address = c.merchant?.primaryLocation?.address;
  const workingHours = c.merchant?.primaryLocation?.workingHours;

  return (
    <div className="detail-block detail-important-info">
      <h2 className="detail-section-title">
        <Info size={18} />
        {t('couponDetail.importantInfoTitle')}
      </h2>
      <ul className="important-info-list">
        {address && (
          <li>
            <MapPin size={15} />
            <div>
              <span className="important-info-label">{t('couponDetail.importantInfo.address')}</span>
              <span>{address}</span>
            </div>
          </li>
        )}
        <li>
          <Calendar size={15} />
          <div>
            <span className="important-info-label">{t('couponDetail.importantInfo.buyUntil')}</span>
            <span>{formatDate(c.buyUntil)}</span>
          </div>
        </li>
        <li>
          <Clock size={15} />
          <div>
            <span className="important-info-label">{t('couponDetail.importantInfo.useUntil')}</span>
            <span>{formatDate(c.useUntil)}</span>
          </div>
        </li>
        {workingHours && (
          <li>
            <Clock size={15} />
            <div>
              <span className="important-info-label">{t('couponDetail.importantInfo.hours')}</span>
              <span>{workingHours}</span>
            </div>
          </li>
        )}
        <li>
          <Info size={15} />
          <div>
            <span className="important-info-label">{t('couponDetail.importantInfo.usage')}</span>
            <span>{t('couponDetail.importantInfo.usageHint')}</span>
          </div>
        </li>
      </ul>
    </div>
  );
}
