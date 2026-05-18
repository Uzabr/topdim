import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { refundsApi } from '../../api/refunds';
import { AlertTriangle, X } from 'lucide-react';
import type { PurchasedCoupon } from '../../api/orders';
import './RefundRequestModal.css';

interface Props {
  coupon: PurchasedCoupon;
  onClose: () => void;
}

export default function RefundRequestModal({ coupon, onClose }: Props) {
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
      setError(e.response?.data?.message || 'Ошибка создания заявки');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (reason.trim().length < 10) {
      setError('Причина должна содержать минимум 10 символов');
      return;
    }
    mutation.mutate();
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card glass-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Запрос на возврат</h3>
          <button className="modal-close" onClick={onClose}><X size={20} /></button>
        </div>

        <div className="modal-coupon-info">
          <span className="modal-coupon-title">{coupon.couponTitle}</span>
          <span className="modal-coupon-code">{coupon.couponCode}</span>
          {coupon.merchantName && <span className="modal-coupon-merchant">{coupon.merchantName}</span>}
        </div>

        <div className="modal-warning">
          <AlertTriangle size={16} />
          <span>После отправки заявки купон будет временно заблокирован от использования.</span>
        </div>

        <div className="modal-info">
          <p>Возврат не происходит мгновенно. Мы рассмотрим заявку, и если возврат будет одобрен, деньги вернутся в течение до 5 рабочих дней.</p>
        </div>

        <form onSubmit={handleSubmit}>
          <label className="modal-label">Причина возврата *</label>
          <textarea
            className="modal-textarea"
            value={reason}
            onChange={(e) => { setReason(e.target.value); setError(''); }}
            placeholder="Опишите причину возврата (минимум 10 символов)"
            rows={4}
            maxLength={1000}
          />
          <div className="modal-char-count">{reason.length}/1000</div>

          {error && <div className="modal-error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="secondary-button" onClick={onClose}>Отмена</button>
            <button
              type="submit"
              className="primary-button"
              disabled={mutation.isPending}
            >
              {mutation.isPending ? 'Отправка...' : 'Отправить заявку на возврат'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
