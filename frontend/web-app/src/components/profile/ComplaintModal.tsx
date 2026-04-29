import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { complaintsApi } from '../../api/complaints';
import { X } from 'lucide-react';
import type { PurchasedCoupon } from '../../api/orders';
import './RefundRequestModal.css';

const SUBJECTS = [
  'Партнёр не принял купон',
  'QR/PIN не сработал',
  'Адрес или контакты неверные',
  'Условия не совпали',
  'Другое',
];

interface Props {
  coupon: PurchasedCoupon;
  onClose: () => void;
}

export default function ComplaintModal({ coupon, onClose }: Props) {
  const [subject, setSubject] = useState(SUBJECTS[0]);
  const [description, setDescription] = useState('');
  const [error, setError] = useState('');
  const queryClient = useQueryClient();

  const mutation = useMutation({
    mutationFn: () => complaintsApi.create({ purchasedCouponId: coupon.id, subject, description }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-complaints'] });
      onClose();
    },
    onError: (err: any) => {
      setError(err.response?.data?.message || 'Ошибка создания обращения');
    },
  });

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (description.trim().length < 10) {
      setError('Описание должно содержать минимум 10 символов');
      return;
    }
    mutation.mutate();
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-card glass-card" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3>Сообщить о проблеме</h3>
          <button className="modal-close" onClick={onClose}><X size={20} /></button>
        </div>

        <div className="modal-coupon-info">
          <span className="modal-coupon-title">{coupon.couponTitle}</span>
          <span className="modal-coupon-code">{coupon.couponCode}</span>
        </div>

        <form onSubmit={handleSubmit}>
          <label className="modal-label">Тема *</label>
          <select
            className="modal-textarea"
            style={{ minHeight: 'auto' }}
            value={subject}
            onChange={(e) => setSubject(e.target.value)}
          >
            {SUBJECTS.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>

          <label className="modal-label" style={{ marginTop: 12 }}>Описание проблемы *</label>
          <textarea
            className="modal-textarea"
            value={description}
            onChange={(e) => { setDescription(e.target.value); setError(''); }}
            placeholder="Подробно опишите проблему (минимум 10 символов)"
            rows={4}
            maxLength={2000}
          />

          {error && <div className="modal-error">{error}</div>}

          <div className="modal-actions">
            <button type="button" className="secondary-button" onClick={onClose}>Отмена</button>
            <button type="submit" className="primary-button" disabled={mutation.isPending}>
              {mutation.isPending ? 'Отправка...' : 'Отправить обращение'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
