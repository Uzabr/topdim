import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { refundsApi, type RefundRequestData } from '../../api/refunds';
import { Clock, CheckCircle, XCircle, Loader2, Receipt } from 'lucide-react';
import { formatDate } from '../../utils/format';
import './RefundsSection.css';

export default function RefundsSection() {
  const { t } = useTranslation();

  const statusMap = useMemo(() => ({
    PENDING: { label: t('profile.refundStatus.pending'), icon: <Clock size={14} />, className: 'refund-badge--pending' },
    APPROVED_PROCESSING: { label: t('profile.refundStatus.approved'), icon: <Loader2 size={14} />, className: 'refund-badge--processing' },
    REFUNDED: { label: t('profile.refundStatus.refunded'), icon: <CheckCircle size={14} />, className: 'refund-badge--refunded' },
    REJECTED: { label: t('profile.refundStatus.rejected'), icon: <XCircle size={14} />, className: 'refund-badge--rejected' },
  }), [t]);

  const { data: refunds = [], isLoading } = useQuery({
    queryKey: ['my-refunds'],
    queryFn: () => refundsApi.getMine(),
    select: (res) => res.data.data,
  });

  if (isLoading) return <div className="profile-loading">{t('profile.loadingRefunds')}</div>;

  if (refunds.length === 0) {
    return (
      <div className="refunds-empty surface-card">
        <Receipt size={40} strokeWidth={1.5} className="section-empty__icon" />
        <h3>{t('profile.refundsSection.emptyTitle')}</h3>
        <p>{t('profile.refundsSection.emptyDesc')}</p>
      </div>
    );
  }

  return (
    <div className="refunds-list">
      {refunds.map((r: RefundRequestData) => {
        const status = statusMap[r.status as keyof typeof statusMap] || statusMap.PENDING;
        return (
          <div key={r.id} className="refund-card surface-card">
            <div className="refund-card__top">
              <div>
                <h4 className="refund-card__title">{r.couponTitle || t('profile.purchasedCoupon.couponFallback')}</h4>
                {r.couponCode && <span className="refund-card__code">{r.couponCode}</span>}
              </div>
              <span className={`refund-badge ${status.className}`}>
                {status.icon} {status.label}
              </span>
            </div>

            <p className="refund-card__reason">{r.reason}</p>

            {r.refundAmount != null && r.refundAmount > 0 && (
              <div className="refund-card__amount">
                {t('profile.refundsSection.refundAmount', { amount: `${r.refundAmount.toLocaleString()} ${t('common.currency.sum')}` })}
              </div>
            )}

            {r.status === 'APPROVED_PROCESSING' && r.expectedRefundAt && (
              <div className="refund-card__info">
                {t('profile.refundsSection.refundUntil', { date: formatDate(r.expectedRefundAt) })}
              </div>
            )}

            {r.adminComment && (
              <div className="refund-card__comment">
                {t('profile.refundsSection.adminComment', { comment: r.adminComment })}
              </div>
            )}

            <div className="refund-card__date">
              {t('profile.refundsSection.created', { date: formatDate(r.createdAt) })}
            </div>
          </div>
        );
      })}
    </div>
  );
}
