import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { refundsApi } from '../../api/refunds';
import { AlertTriangle, X } from 'lucide-react';
import type { PurchasedCoupon } from '../../api/orders';
import './RefundRequestModal.css';

interface Props {
  coupon: PurchasedCoupon;
  onClose: () => void;
}

export default function RefundRequestModal({ coupon, onClose }: Props) {
  const { t } = useTranslation();
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => refundsApi.create({ purchasedCouponId: coupon.id, reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-coupons'] });
      queryClient.invalidateQueries({ queryKey: ['my-refunds'] });
      onClose();
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || t('profile.refundModal.error'));
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (reason.trim().length < 10) {
      setError(t('profile.refundModal.reasonMin'));
      return;
    }
    mutation.mutate();
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card glass-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{t('profile.refundModal.title')}</h3>
          <button className="modal-close" onClick={onClose}><X size={20} /></button>
        </div>

        <div className="modal-coupon-info">
          <span className="modal-coupon-title">{coupon.couponTitle}</span>
          <span className="modal-coupon-code">{coupon.couponCode}</span>
          {coupon.merchantName && <span className="modal-coupon-merchant">{coupon.merchantName}</span>}
        </div>

        <div className="modal-warning">
          <AlertTriangle size={16} />
          <span>{t('profile.refundModal.blockedHint')}</span>
        </div>

        <div className="modal-info">
          <p>{t('profile.refundModal.processingHint')}</p>
        </div>

        <form onSubmit={handleSubmit}>
          <label className="modal-label">{t('profile.refundModal.reasonLabel')}</label>
          <textarea
            className="modal-textarea"
            value={reason}
            onChange={(e) => { setReason(e.target.value); setError(''); }}
            placeholder={t('profile.refundModal.reasonPlaceholder')}
            rows={4}
            maxLength={1000}
          />
          <div className="modal-char-count">{reason.length}/1000</div>

          {error && <div className="modal-error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="secondary-button" onClick={onClose}>{t('common.cancel')}</button>
            <button
              type="submit"
              className="primary-button"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? t('common.submitting') : t('profile.refundModal.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
