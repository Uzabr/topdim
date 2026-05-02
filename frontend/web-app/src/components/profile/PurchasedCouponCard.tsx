import { useState } from 'react';
import { AlertCircle, CalendarClock, CheckCircle, Clock, Copy, MapPin, Phone, RotateCcw, MessageSquare, Loader2, Ban, ChevronDown, Store } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import type { PurchasedCoupon } from '../../api/orders';
import './PurchasedCouponCard.css';

interface PurchasedCouponCardProps {
  coupon: PurchasedCoupon;
  onRefundRequest?: (coupon: PurchasedCoupon) => void;
  onComplaintRequest?: (coupon: PurchasedCoupon) => void;
}

function formatDate(date?: string): string {
  if (!date) return 'не указан';
  return new Date(date).toLocaleDateString('ru-RU');
}

function getStatusLabel(status: PurchasedCoupon['status']): string {
  switch (status) {
    case 'ACTIVE': return 'Активно';
    case 'USED': return 'Использовано';
    case 'EXPIRED': return 'Истекло';
    case 'REFUND_PENDING': return 'Возврат на рассмотрении';
    case 'REFUNDED': return 'Возвращён';
    case 'CANCELLED': return 'Отменено';
    default: return status;
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

function getStatusMeta(coupon: PurchasedCoupon): string {
  if (coupon.status === 'USED' && coupon.usedAt) {
    return `Использован: ${formatDate(coupon.usedAt)}`;
  }
  if (coupon.status === 'REFUNDED') {
    return 'Возврат завершён';
  }
  if (coupon.status === 'REFUND_PENDING') {
    if (coupon.refundStatus === 'APPROVED_PROCESSING' && coupon.refundExpectedAt) {
      return `Одобрено. Ожидайте до ${formatDate(coupon.refundExpectedAt)}`;
    }
    return 'Ждёт решения партнёра';
  }
  if (coupon.status === 'EXPIRED') {
    return 'Срок действия истёк';
  }
  if (coupon.status === 'CANCELLED') {
    return 'Купон отменён';
  }
  return `Действует до: ${formatDate(coupon.expiresAt)}`;
}

export default function PurchasedCouponCard({ coupon, onRefundRequest, onComplaintRequest }: PurchasedCouponCardProps) {
  const [isExpanded, setIsExpanded] = useState(false);

  const copyCode = async () => {
    if (!coupon.couponCode) return;
    await navigator.clipboard.writeText(coupon.couponCode);
  };

  const isActive = coupon.status === 'ACTIVE';
  const canShowPass = isActive && Boolean(coupon.qrToken || coupon.couponCode);
  const canRefund = coupon.status === 'ACTIVE';
  const canComplain = ['ACTIVE', 'USED', 'EXPIRED'].includes(coupon.status);

  return (
    <article className={`coupon-ticket coupon-ticket--${coupon.status.toLowerCase()} ${isExpanded ? 'is-expanded' : ''}`}>
      
      {/* ── ТЕЛО БИЛЕТА (Верхняя часть) ── */}
      <div className="coupon-ticket__header">
        <div className="coupon-ticket__topline">
          <span className={`coupon-ticket__badge coupon-ticket__badge--${coupon.status.toLowerCase()}`}>
            {getStatusIcon(coupon.status)}
            {getStatusLabel(coupon.status)}
          </span>
          <span className="coupon-ticket__meta">{getStatusMeta(coupon)}</span>
        </div>

        <h3 className="coupon-ticket__title">{coupon.couponTitle}</h3>
        {coupon.optionTitle && <p className="coupon-ticket__option">{coupon.optionTitle}</p>}
        
        <div className="coupon-ticket__merchant-info">
          <Store size={16} />
          <span>{coupon.merchantName || 'Партнёр TopDim'}</span>
        </div>
      </div>

      {/* ── ЛИНИЯ ОТРЫВА (Перфорация) ── */}
      <div className="coupon-ticket__separator">
        <div className="coupon-ticket__notch coupon-ticket__notch--left"></div>
        <div className="coupon-ticket__dash"></div>
        <div className="coupon-ticket__notch coupon-ticket__notch--right"></div>
      </div>

      {/* ── КНОПКА РАСКРЫТИЯ ── */}
      <button 
        className="coupon-ticket__toggle" 
        onClick={() => setIsExpanded(!isExpanded)}
        aria-expanded={isExpanded}
      >
        <span>{isExpanded ? 'Скрыть детали' : 'QR-код и детали'}</span>
        <ChevronDown size={18} className="coupon-ticket__toggle-icon" />
      </button>

      {/* ── РАСКРЫВАЮЩАЯСЯ ЧАСТЬ (Анимация через Grid) ── */}
      <div className="coupon-ticket__drawer">
        <div className="coupon-ticket__drawer-inner">
          
          {/* QR и ПИН */}
          {canShowPass && (
            <div className="coupon-ticket__pass-section">
              {coupon.qrToken && (
                <div className="coupon-ticket__qr-container">
                  <div className="coupon-ticket__qr-frame">
                    <QRCodeSVG
                      value={buildQrPayload(coupon.qrToken)}
                      size={160}
                      level="M"
                      includeMargin
                    />
                  </div>
                  <p className="coupon-ticket__qr-hint">Покажите QR-код кассиру</p>
                </div>
              )}

              <div className="coupon-ticket__pin-container">
                <span className="coupon-ticket__pin-label">или продиктуйте код</span>
                <div className="coupon-ticket__pin-value">
                  <strong>{coupon.couponCode || '—'}</strong>
                  {coupon.couponCode && (
                    <button type="button" onClick={copyCode} className="coupon-ticket__copy-btn" title="Скопировать">
                      <Copy size={16} />
                    </button>
                  )}
                </div>
              </div>
            </div>
          )}

          {/* Детали партнера */}
          <div className="coupon-ticket__details-grid">
            {coupon.merchantAddress && (
              <div className="coupon-ticket__detail-item">
                <MapPin size={16} />
                <div>
                  <span>Адрес</span>
                  <p>{coupon.merchantAddress}</p>
                </div>
              </div>
            )}
            {coupon.merchantPhone && (
              <a href={`tel:${coupon.merchantPhone}`} className="coupon-ticket__detail-item is-link">
                <Phone size={16} />
                <div>
                  <span>Телефон</span>
                  <p>{coupon.merchantPhone}</p>
                </div>
              </a>
            )}
            {coupon.merchantWorkingHours && (
              <div className="coupon-ticket__detail-item">
                <CalendarClock size={16} />
                <div>
                  <span>Время работы</span>
                  <p>{coupon.merchantWorkingHours}</p>
                </div>
              </div>
            )}
          </div>

          {/* Действия: Возврат / Жалоба */}
          {((canRefund && onRefundRequest) || (canComplain && onComplaintRequest)) && (
            <div className="coupon-ticket__actions">
              {canRefund && onRefundRequest && (
                <button
                  type="button"
                  className="coupon-ticket__action-btn coupon-ticket__action-btn--refund"
                  onClick={() => onRefundRequest(coupon)}
                >
                  <RotateCcw size={15} />
                  Оформить возврат
                </button>
              )}
              {canComplain && onComplaintRequest && (
                <button
                  type="button"
                  className="coupon-ticket__action-btn coupon-ticket__action-btn--complain"
                  onClick={() => onComplaintRequest(coupon)}
                >
                  <MessageSquare size={15} />
                  Проблема с купоном?
                </button>
              )}
            </div>
          )}
          
        </div>
      </div>
      
    </article>
  );
}
