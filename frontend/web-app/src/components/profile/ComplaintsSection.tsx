import { useQuery } from '@tanstack/react-query';
import { complaintsApi, type ComplaintData } from '../../api/complaints';
import { Clock, CheckCircle, XCircle, Eye } from 'lucide-react';
import './ComplaintsSection.css';

const STATUS_MAP: Record<string, { label: string; icon: React.ReactNode; className: string }> = {
  PENDING: { label: 'На рассмотрении', icon: <Clock size={14} />, className: 'complaint-badge--pending' },
  IN_REVIEW: { label: 'В работе', icon: <Eye size={14} />, className: 'complaint-badge--review' },
  RESOLVED: { label: 'Обработано', icon: <CheckCircle size={14} />, className: 'complaint-badge--resolved' },
  REJECTED: { label: 'Отклонено', icon: <XCircle size={14} />, className: 'complaint-badge--rejected' },
};

export default function ComplaintsSection() {
  const { data, isLoading } = useQuery({
    queryKey: ['my-complaints'],
    queryFn: () => complaintsApi.getMine(),
    select: (res) => res.data.data,
  });

  const complaints = data?.content ?? [];

  if (isLoading) return <div className="profile-loading">Загрузка обращений...</div>;

  if (complaints.length === 0) {
    return (
      <div className="complaints-empty glass-card">
        <span style={{ fontSize: '2rem' }}>📨</span>
        <h3>Нет обращений</h3>
        <p>Здесь будут ваши обращения о проблемах.</p>
      </div>
    );
  }

  return (
    <div className="complaints-list">
      {complaints.map((c: ComplaintData) => {
        const status = STATUS_MAP[c.status] || STATUS_MAP.PENDING;
        return (
          <div key={c.id} className="complaint-card glass-card">
            <div className="complaint-card__top">
              <div>
                <h4 className="complaint-card__subject">{c.subject}</h4>
                {c.couponTitle && <span className="complaint-card__coupon">{c.couponTitle}</span>}
              </div>
              <span className={`complaint-badge ${status.className}`}>
                {status.icon} {status.label}
              </span>
            </div>
            <p className="complaint-card__desc">{c.description}</p>
            {c.resolution && (
              <div className="complaint-card__resolution">
                Ответ: {c.resolution}
              </div>
            )}
            <div className="complaint-card__date">
              {new Date(c.createdAt).toLocaleDateString('ru-RU')}
            </div>
          </div>
        );
      })}
    </div>
  );
}
