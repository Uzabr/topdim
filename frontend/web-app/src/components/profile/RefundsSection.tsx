import { useQuery } from '@tanstack/react-query';
import { refundsApi, type RefundRequestData } from '../../api/refunds';
import { Clock, CheckCircle, XCircle, Loader2 } from 'lucide-react';
import './RefundsSection.css';

const STATUS_MAP: Record<string, { label: string; icon: React.ReactNode; className: string }> = {
  PENDING: { label: 'На рассмотрении', icon: <Clock size={14} />, className: 'refund-badge--pending' },
  APPROVED_PROCESSING: { label: 'Одобрен, ожидание возврата', icon: <Loader2 size={14} />, className: 'refund-badge--processing' },
  REFUNDED: { label: 'Возврат завершён', icon: <CheckCircle size={14} />, className: 'refund-badge--refunded' },
  REJECTED: { label: 'Отклонён', icon: <XCircle size={14} />, className: 'refund-badge--rejected' },
};

export default function RefundsSection() {
  const { data: refunds = [], isLoading } = useQuery({
    queryKey: ['my-refunds'],
    queryFn: () => refundsApi.getMine(),
    select: (res) => res.data.data,
  });

  if (isLoading) return <div className="profile-loading">Загрузка заявок на возврат...</div>;

  if (refunds.length === 0) {
    return (
      <div className="refunds-empty glass-card">
        <span style={{ fontSize: '2rem' }}>📋</span>
        <h3>Нет заявок на возврат</h3>
        <p>Запросить возврат можно в карточке активного купона.</p>
      </div>
    );
  }

  return (
    <div className="refunds-list">
      {refunds.map((r: RefundRequestData) => {
        const status = STATUS_MAP[r.status] || STATUS_MAP.PENDING;
        return (
          <div key={r.id} className="refund-card glass-card">
            <div className="refund-card__top">
              <div>
                <h4 className="refund-card__title">{r.couponTitle || 'Купон'}</h4>
                {r.couponCode && <span className="refund-card__code">{r.couponCode}</span>}
              </div>
              <span className={`refund-badge ${status.className}`}>
                {status.icon} {status.label}
              </span>
            </div>

            <p className="refund-card__reason">{r.reason}</p>

            {r.refundAmount != null && r.refundAmount > 0 && (
              <div className="refund-card__amount">
                Сумма возврата: <strong>{r.refundAmount.toLocaleString()} сум</strong>
              </div>
            )}

            {r.status === 'APPROVED_PROCESSING' && r.expectedRefundAt && (
              <div className="refund-card__info">
                Деньги вернутся до: <strong>{new Date(r.expectedRefundAt).toLocaleDateString('ru-RU')}</strong>
              </div>
            )}

            {r.adminComment && (
              <div className="refund-card__comment">
                Комментарий администратора: {r.adminComment}
              </div>
            )}

            <div className="refund-card__date">
              Создано: {new Date(r.createdAt).toLocaleDateString('ru-RU')}
            </div>
          </div>
        );
      })}
    </div>
  );
}
