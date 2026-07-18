import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { complaintsApi } from '../../api/complaints';
import { X } from 'lucide-react';
import type { PurchasedCoupon } from '../../api/orders';
import './RefundRequestModal.css';

interface Props {
  coupon: PurchasedCoupon;
  onClose: () => void;
}

export default function ComplaintModal({ coupon, onClose }: Props) {
  const { t } = useTranslation();
  const subjects = useMemo(() => [
    t('profile.complaintModal.topics.notAccepted'),
    t('profile.complaintModal.topics.qrFailed'),
    t('profile.complaintModal.topics.wrongAddress'),
    t('profile.complaintModal.topics.termsMismatch'),
    t('profile.complaintModal.topics.other'),
  ], [t]);
  const [subject, setSubject] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();

  const activeSubject = subject || subjects[0];

  const mutation = useMutation({
    mutationFn: () => complaintsApi.create({ purchasedCouponId: coupon.id, subject: activeSubject, description }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-complaints'] });
      onClose();
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      setError(e.response?.data?.message || t('profile.complaintModal.error'));
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (description.trim().length < 10) {
      setError(t('profile.complaintModal.descMin'));
      return;
    }
    mutation.mutate();
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card surface-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>{t('profile.complaintModal.title')}</h3>
          <button className="modal-close" onClick={onClose}><X size={20} /></button>
        </div>

        <div className="modal-coupon-info">
          <span className="modal-coupon-title">{coupon.couponTitle}</span>
          <span className="modal-coupon-code">{coupon.couponCode}</span>
        </div>

        <div className="modal-info">
          <p>{t('profile.complaintModal.desc')}</p>
        </div>

        <form onSubmit={handleSubmit}>
          <label className="modal-label">{t('profile.complaintModal.topicLabel')}</label>
          <select
            className="modal-textarea"
            style={{ minHeight: 'auto' }}
            value={activeSubject}
            onChange={(e) => setSubject(e.target.value)}
          >
            {subjects.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>

          <label className="modal-label" style={{ marginTop: 12 }}>{t('profile.complaintModal.descLabel')}</label>
          <textarea
            className="modal-textarea"
            value={description}
            onChange={(e) => { setDescription(e.target.value); setError(''); }}
            placeholder={t('profile.complaintModal.descPlaceholder')}
            rows={4}
            maxLength={2000}
          />

          {error && <div className="modal-error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="secondary-button" onClick={onClose}>{t('common.cancel')}</button>
            <button type="submit" className="primary-button" disabled={mutation.isPending}>
              {mutation.isPending ? t('common.submitting') : t('profile.complaintModal.submit')}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
