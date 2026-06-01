import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertCircle, CalendarClock, CheckCircle, Clock, Copy, MapPin, Phone, RotateCcw, MessageSquare, Loader2, Ban, ChevronDown, Store, Star } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import type { PurchasedCoupon } from '../../api/orders';
import { Link } from 'react-router-dom';
import { useLocalePath } from '../../hooks/useLocalePath';
import { formatDate as formatLocaleDate } from '../../utils/format';
import './PurchasedCouponCard.css';

interface PurchasedCouponCardProps {
  coupon: PurchasedCoupon;
  onRefundRequest?: (coupon: PurchasedCoupon) => void;
  onComplaintRequest?: (coupon: PurchasedCoupon) => void;
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
  const { t } = useTranslation();
  const [isExpanded, setIsExpanded] = useState(false);
  const lp = useLocalePath();

  const formatDate = (date?: string) => date ? formatLocaleDate(date) : t('common.notSpecified');

  const getStatusLabel = (status: PurchasedCoupon['status']) => {
    switch (status) {
      case 'ACTIVE': return t('profile.purchasedCoupon.status.active');
      case 'USED': return t('profile.purchasedCoupon.status.used');
      case 'EXPIRED': return t('profile.purchasedCoupon.status.expired');
      case 'REFUND_PENDING': return t('profile.purchasedCoupon.status.refundPending');
      case 'REFUNDED': return t('profile.purchasedCoupon.status.refunded');
      case 'CANCELLED': return t('profile.purchasedCoupon.status.cancelled');
      default: return status;
    }
  };

  const getStatusMeta = (c: PurchasedCoupon) => {
    if (c.status === 'USED' && c.usedAt) {
      return t('profile.purchasedCoupon.usedAt', { date: formatDate(c.usedAt) });
    }
    if (c.status === 'REFUNDED') return t('profile.purchasedCoupon.refundDone');
    if (c.status === 'REFUND_PENDING') {
      if (c.refundStatus === 'APPROVED_PROCESSING' && c.refundExpectedAt) {
        return t('profile.purchasedCoupon.refundApproved', { date: formatDate(c.refundExpectedAt) });
      }
      return t('profile.purchasedCoupon.refundWaiting');
    }
    if (c.status === 'EXPIRED') return t('profile.purchasedCoupon.expired');
    if (c.status === 'CANCELLED') return t('profile.purchasedCoupon.cancelled');
    return t('profile.purchasedCoupon.validUntil', { date: formatDate(c.expiresAt) });
  };

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
          <span>{coupon.merchantName || t('profile.purchasedCoupon.defaultMerchant')}</span>
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
        <span>{isExpanded ? t('profile.purchasedCoupon.toggleHide') : t('profile.purchasedCoupon.toggleShow')}</span>
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
                  <p className="coupon-ticket__qr-hint">{t('profile.purchasedCoupon.qrHint')}</p>
                </div>
              )}

              <div className="coupon-ticket__pin-container">
                <span className="coupon-ticket__pin-label">{t('profile.purchasedCoupon.pinHint')}</span>
                <div className="coupon-ticket__pin-value">
                  <strong>{coupon.couponCode || '—'}</strong>
                  {coupon.couponCode && (
                    <button type="button" onClick={copyCode} className="coupon-ticket__copy-btn" title={t('common.copy')}>
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
                  <span>{t('profile.purchasedCoupon.address')}</span>
                  <p>{coupon.merchantAddress}</p>
                </div>
              </div>
            )}
            {coupon.merchantPhone && (
              <a href={`tel:${coupon.merchantPhone}`} className="coupon-ticket__detail-item is-link">
                <Phone size={16} />
                <div>
                  <span>{t('common.phone')}</span>
                  <p>{coupon.merchantPhone}</p>
                </div>
              </a>
            )}
            {coupon.merchantWorkingHours && (
              <div className="coupon-ticket__detail-item">
                <CalendarClock size={16} />
                <div>
                  <span>{t('profile.purchasedCoupon.hours')}</span>
                  <p>{coupon.merchantWorkingHours}</p>
                </div>
              </div>
            )}
          </div>

          {/* Действия: Возврат / Жалоба */}
          {((canRefund && onRefundRequest) || (canComplain && onComplaintRequest)) && (
            <div className="coupon-ticket__actions">
              {coupon.status === 'USED' && (
                <Link
                  className="coupon-ticket__action-btn coupon-ticket__action-btn--review"
                  to={lp(`/coupons/${coupon.couponOfferId}`) + '?tab=reviews'}
                >
                  <Star size={15} />
                  {t('profile.purchasedCoupon.leaveReview')}
                </Link>
              )}
              {canRefund && onRefundRequest && (
                <button
                  type="button"
                  className="coupon-ticket__action-btn coupon-ticket__action-btn--refund"
                  onClick={() => onRefundRequest(coupon)}
                >
                  <RotateCcw size={15} />
                  {t('profile.purchasedCoupon.requestRefund')}
                </button>
              )}
              {canComplain && onComplaintRequest && (
                <button
                  type="button"
                  className="coupon-ticket__action-btn coupon-ticket__action-btn--complain"
                  onClick={() => onComplaintRequest(coupon)}
                >
                  <MessageSquare size={15} />
                  {t('profile.purchasedCoupon.complaint')}
                </button>
              )}
            </div>
          )}
          
        </div>
      </div>
      
    </article>
  );
}
