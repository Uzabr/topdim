import { useTranslation } from 'react-i18next';
import { QRCodeSVG } from 'qrcode.react';
import type { PurchasedCoupon } from '../../api/orders';
import { daysUntil, formatDate, formatPrice } from '../../utils/format';
import { buildQrPayload } from '../../utils/coupon';
import { getCouponActions } from './couponActions';
import './CouponTicket.css';

interface CouponTicketProps {
  coupon: PurchasedCoupon;
  hasComplaint: boolean;
  onRefund: (coupon: PurchasedCoupon) => void;
  onComplain: (coupon: PurchasedCoupon) => void;
}

/**
 * Ближайший к истечению купон — чёрный билет с перфорацией.
 * QR открыт сразу, не за кликом (design_handoff_sizbiz → «Профиль», таб «Мои купоны»).
 */
export default function CouponTicket({ coupon, hasComplaint, onRefund, onComplain }: CouponTicketProps) {
  const { t } = useTranslation();

  const days = coupon.expiresAt ? daysUntil(coupon.expiresAt) : null;
  const place = [coupon.merchantName, coupon.merchantAddress].filter(Boolean).join(' · ');
  const actions = getCouponActions(coupon.status, hasComplaint, false);

  return (
    <article className="ticket">
      <div className="ticket__top">
        <div className="ticket__body">
          <div className="ticket__badges">
            <span className="ticket__label">{t('profile.ticket.nearest')}</span>
            {days !== null && (
              <span className="ticket__expiry">
                {days <= 0
                  ? t('profile.ticket.expiresToday')
                  : t('profile.ticket.expiresInDays', { count: days })}
              </span>
            )}
            {hasComplaint && (
              <span className="ticket__complaint">{t('profile.complaintPending')}</span>
            )}
          </div>

          <h2 className="ticket__title">{coupon.couponTitle}</h2>

          <p className="ticket__meta">
            {place}
            {coupon.expiresAt && ` · ${t('profile.ticket.until', { date: formatDate(coupon.expiresAt) })}`}
          </p>

          <div className="ticket__status">
            {/* Макет показывает уплаченную цену; пока backend её не отдаёт (pricePaid) —
                честный фолбэк на название опции. См. TODO(backend) в api/orders.ts. */}
            {coupon.pricePaid != null ? (
              <span className="ticket__price">{formatPrice(coupon.pricePaid)}</span>
            ) : (
              <span className="ticket__option">{coupon.optionTitle}</span>
            )}
            <span className="ticket__paid">
              {coupon.status === 'REFUND_PENDING'
                ? t('profile.purchasedCoupon.status.refundPending')
                : t('profile.ticket.paid')}
            </span>
          </div>
        </div>

        {coupon.qrToken && (
          <div className="ticket__qr">
            <QRCodeSVG value={buildQrPayload(coupon.qrToken)} size={108} level="M" />
          </div>
        )}
      </div>

      <div className="ticket__perforation">
        <span className="ticket__notch ticket__notch--left" />
        <span className="ticket__notch ticket__notch--right" />
      </div>

      <div className="ticket__bottom">
        <p className="ticket__code">
          {t('profile.ticket.code')}{' '}
          <span className="ticket__code-value">{coupon.couponCode || '—'}</span>
          {' — '}
          {t('profile.ticket.showAtCashier')}
        </p>

        <div className="ticket__actions">
          {actions.canRefund && (
            <button type="button" className="ticket__link" onClick={() => onRefund(coupon)}>
              {t('profile.refundMoney')}
            </button>
          )}
          {actions.canComplain && (
            <button type="button" className="ticket__link" onClick={() => onComplain(coupon)}>
              {t('profile.complain')}
            </button>
          )}
        </div>
      </div>
    </article>
  );
}
