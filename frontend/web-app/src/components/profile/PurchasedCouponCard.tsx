import { AlertCircle, CalendarClock, CheckCircle, Clock, Copy, MapPin, Phone, QrCode, Store, RotateCcw, MessageSquare, Loader2, Ban } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import type { PurchasedCoupon } from '../../api/orders';
import './PurchasedCouponCard.css';

interface PurchasedCouponCardProps {
  coupon: PurchasedCoupon;
  onRefundRequest?: (coupon: PurchasedCoupon) => void;
  onComplaintRequest?: (coupon: PurchasedCoupon) => void;
}

function formatDate(date?: string): string {
  if (!date) {
    return 'не указан';
  }

  return new Date(date).toLocaleDateString('ru-RU');
}

function getStatusLabel(status: PurchasedCoupon['status']): string {
  switch (status) {
    case 'ACTIVE':
      return 'Активно';
    case 'USED':
      return 'Использовано';
    case 'EXPIRED':
      return 'Истекло';
    case 'REFUND_PENDING':
      return 'Возврат на рассмотрении';
    case 'REFUNDED':
      return 'Возвращён';
    case 'CANCELLED':
      return 'Отменено';
    default:
      return status;
  }
}

function getStatusIcon(status: PurchasedCoupon['status']) {
  if (status === 'ACTIVE') return <Clock size={16} />;
  if (status === 'USED') return <CheckCircle size={16} />;
  if (status === 'REFUND_PENDING') return <Loader2 size={16} />;
  if (status === 'REFUNDED') return <RotateCcw size={16} />;
  if (status === 'CANCELLED') return <Ban size={16} />;
  return <AlertCircle size={16} />;
}

function buildQrPayload(qrToken?: string): string {
  return qrToken ? `TOPDIM-QR:${qrToken}` : '';
}

export default function PurchasedCouponCard({ coupon, onRefundRequest, onComplaintRequest }: PurchasedCouponCardProps) {
  const isActive = coupon.status === 'ACTIVE';

  const copyCode = async () => {
    await navigator.clipboard.writeText(coupon.couponCode);
  };

  // Determine available actions
  const canRefund = coupon.status === 'ACTIVE';
  const canComplain = ['ACTIVE', 'USED', 'EXPIRED'].includes(coupon.status);

  return (
    <article className={`purchased-coupon-card purchased-coupon-card--${coupon.status.toLowerCase()}`}>
      <div className="purchased-coupon-card__header">
        <div>
          <p className="purchased-coupon-card__eyebrow">Купон #{coupon.id}</p>
          <h3>{coupon.couponTitle}</h3>
          <p>{coupon.optionTitle}</p>
        </div>
        <span className={`purchased-coupon-card__status purchased-coupon-card__status--${coupon.status.toLowerCase()}`}>
          {getStatusIcon(coupon.status)}
          {getStatusLabel(coupon.status)}
        </span>
      </div>

      <div className="purchased-coupon-card__code-box">
        <div>
          <span className="purchased-coupon-card__label">Покажите сотруднику</span>
          <strong>{coupon.couponCode || 'Код недоступен'}</strong>
        </div>
        {isActive && coupon.couponCode ? (
          <button type="button" onClick={copyCode}>
            <Copy size={16} />
            Скопировать
          </button>
        ) : null}
      </div>

      {isActive && coupon.qrToken ? (
        <div className="purchased-coupon-card__qr-box">
          <div className="purchased-coupon-card__qr-frame" aria-label="QR-код купона TopDim">
            <QRCodeSVG
              value={buildQrPayload(coupon.qrToken)}
              size={164}
              level="M"
              includeMargin
            />
          </div>
          <div className="purchased-coupon-card__qr-copy">
            <span className="purchased-coupon-card__label">QR-код купона</span>
            <p>Покажите этот QR-код кассиру партнёра для погашения.</p>
          </div>
        </div>
      ) : null}

      <div className="purchased-coupon-card__usage">
        <div className="purchased-coupon-card__usage-item">
          <Store size={16} />
          <span>{coupon.merchantName || 'Название партнёра недоступно'}</span>
        </div>
        <div className="purchased-coupon-card__usage-item">
          <MapPin size={16} />
          <span>{coupon.merchantAddress || 'Адрес партнёра уточните перед визитом'}</span>
        </div>
        {coupon.merchantPhone ? (
          <a className="purchased-coupon-card__usage-item" href={`tel:${coupon.merchantPhone}`}>
            <Phone size={16} />
            <span>{coupon.merchantPhone}</span>
          </a>
        ) : null}
        {coupon.merchantWorkingHours ? (
          <div className="purchased-coupon-card__usage-item">
            <CalendarClock size={16} />
            <span>{coupon.merchantWorkingHours}</span>
          </div>
        ) : null}
      </div>

      <div className="purchased-coupon-card__footer">
        <div>
          {coupon.status === 'USED' && coupon.usedAt
            ? `Использован: ${formatDate(coupon.usedAt)}`
            : coupon.status === 'REFUNDED'
            ? 'Возврат завершён'
            : coupon.status === 'REFUND_PENDING'
            ? 'Ожидает рассмотрения'
            : `Действует до: ${formatDate(coupon.expiresAt)}`}
        </div>
        {coupon.qrToken && isActive ? (
          <div className="purchased-coupon-card__qr-note">
            <QrCode size={16} />
            QR-код доступен для проверки партнёром
          </div>
        ) : null}
      </div>

      {/* Refund expected date info */}
      {coupon.status === 'REFUND_PENDING' && coupon.refundStatus === 'APPROVED_PROCESSING' && coupon.refundExpectedAt && (
        <div className="purchased-coupon-card__refund-info">
          Возврат одобрен. Деньги вернутся до {formatDate(coupon.refundExpectedAt)}.
        </div>
      )}

      {/* Action buttons */}
      {(canRefund || canComplain) && (onRefundRequest || onComplaintRequest) && (
        <div className="purchased-coupon-card__actions">
          {canRefund && onRefundRequest && (
            <button className="purchased-coupon-card__action-btn purchased-coupon-card__action-btn--refund" onClick={() => onRefundRequest(coupon)}>
              <RotateCcw size={14} /> Запросить возврат
            </button>
          )}
          {canComplain && onComplaintRequest && (
            <button className="purchased-coupon-card__action-btn purchased-coupon-card__action-btn--complaint" onClick={() => onComplaintRequest(coupon)}>
              <MessageSquare size={14} /> Сообщить о проблеме
            </button>
          )}
        </div>
      )}

      {isActive && !onRefundRequest ? (
        <div className="purchased-coupon-card__help">
          Если партнёр не принимает купон, покажите этот экран и обратитесь в поддержку TopDim.
        </div>
      ) : null}
    </article>
  );
}
