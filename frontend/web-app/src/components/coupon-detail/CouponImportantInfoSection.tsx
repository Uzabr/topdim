import type { CouponOffer } from '../../api/coupons';
import { Calendar, Clock, MapPin, Info } from 'lucide-react';
import { formatDate } from '../../utils/format';

interface CouponImportantInfoSectionProps {
  coupon: CouponOffer;
}

export default function CouponImportantInfoSection({ coupon }: CouponImportantInfoSectionProps) {
  const c = coupon;
  const address = c.merchant?.primaryLocation?.address;
  const workingHours = c.merchant?.primaryLocation?.workingHours;

  return (
    <div className="detail-block detail-important-info">
      <h2 className="detail-section-title">
        <Info size={18} />
        Важная информация
      </h2>
      <ul className="important-info-list">
        {address && (
          <li>
            <MapPin size={15} />
            <div>
              <span className="important-info-label">Адрес</span>
              <span>{address}</span>
            </div>
          </li>
        )}
        <li>
          <Calendar size={15} />
          <div>
            <span className="important-info-label">Купить до</span>
            <span>{formatDate(c.buyUntil)}</span>
          </div>
        </li>
        <li>
          <Clock size={15} />
          <div>
            <span className="important-info-label">Использовать до</span>
            <span>{formatDate(c.useUntil)}</span>
          </div>
        </li>
        {workingHours && (
          <li>
            <Clock size={15} />
            <div>
              <span className="important-info-label">Часы работы</span>
              <span>{workingHours}</span>
            </div>
          </li>
        )}
        <li>
          <Info size={15} />
          <div>
            <span className="important-info-label">Использование купона</span>
            <span>Покажите QR/PIN-код из личного кабинета при посещении</span>
          </div>
        </li>
      </ul>
    </div>
  );
}
