import { useTranslation } from 'react-i18next';
import { QRCodeSVG } from 'qrcode.react';
import type { PurchasedCoupon } from '../../api/orders';
import { formatDate } from '../../utils/format';
import { buildQrPayload } from '../../utils/coupon';
import { getCouponActions } from './couponActions';
import './ActiveCouponCard.css';

interface ActiveCouponCardProps {
  coupon: PurchasedCoupon;
  hasComplaint: boolean;
  onRefund: (coupon: PurchasedCoupon) => void;
  onComplain: (coupon: PurchasedCoupon) => void;
}

/** Остальные активные купоны — белые карточки с QR 84px. */
export default function ActiveCouponCard({ coupon, hasComplaint, onRefund, onComplain }: ActiveCouponCardProps) {
  const { t } = useTranslation();
  const actions = getCouponActions(coupon.status, hasComplaint, false);

  return (
    <article className="active-coupon">
      <div className="active-coupon__qr">
        {coupon.qrToken ? (
          <QRCodeSVG value={buildQrPayload(coupon.qrToken)} size={68} level="M" />
        ) : (
          <span className="active-coupon__code">{coupon.couponCode}</span>
        )}
      </div>

      <div className="active-coupon__body">
        <h3 className="active-coupon__title">{coupon.couponTitle}</h3>
        <p className="active-coupon__merchant">{coupon.merchantName}</p>

        {coupon.expiresAt && (
          <p className="active-coupon__until">
            {t('profile.ticket.until', { date: formatDate(coupon.expiresAt) })}
          </p>
        )}

        {hasComplaint && (
          <p className="active-coupon__complaint">
            <span>{t('profile.complaintPending')}</span>
          </p>
        )}

        <div className="active-coupon__actions">
          {actions.canRefund && (
            <button type="button" className="active-coupon__link" onClick={() => onRefund(coupon)}>
              {t('profile.refundShort')}
            </button>
          )}
          {actions.canComplain && (
            <button type="button" className="active-coupon__link" onClick={() => onComplain(coupon)}>
              {t('profile.complain')}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
