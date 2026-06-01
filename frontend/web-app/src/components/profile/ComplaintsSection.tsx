import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { complaintsApi, type ComplaintData } from '../../api/complaints';
import { Clock, CheckCircle, XCircle, Eye } from 'lucide-react';
import { formatDate } from '../../utils/format';
import './ComplaintsSection.css';

export default function ComplaintsSection() {
  const { t } = useTranslation();

  const statusMap = useMemo(() => ({
    PENDING: { label: t('profile.complaintStatus.pending'), icon: <Clock size={14} />, className: 'complaint-badge--pending' },
    IN_REVIEW: { label: t('profile.complaintStatus.inReview'), icon: <Eye size={14} />, className: 'complaint-badge--review' },
    RESOLVED: { label: t('profile.complaintStatus.resolved'), icon: <CheckCircle size={14} />, className: 'complaint-badge--resolved' },
    REJECTED: { label: t('profile.complaintStatus.rejected'), icon: <XCircle size={14} />, className: 'complaint-badge--rejected' },
  }), [t]);

  const { data, isLoading } = useQuery({
    queryKey: ['my-complaints'],
    queryFn: () => complaintsApi.getMine(),
    select: (res) => res.data.data,
  });

  const complaints = data?.content ?? [];

  if (isLoading) return <div className="profile-loading">{t('profile.loadingComplaints')}</div>;

  if (complaints.length === 0) {
    return (
      <div className="complaints-empty glass-card">
        <span style={{ fontSize: '2rem' }}>📨</span>
        <h3>{t('profile.complaintsSection.emptyTitle')}</h3>
        <p>{t('profile.complaintsSection.emptyDesc')}</p>
      </div>
    );
  }

  return (
    <div className="complaints-list">
      {complaints.map((c: ComplaintData) => {
        const status = statusMap[c.status as keyof typeof statusMap] || statusMap.PENDING;
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
                {t('profile.complaintsSection.answer', { text: c.resolution })}
              </div>
            )}
            <div className="complaint-card__date">
              {formatDate(c.createdAt)}
            </div>
          </div>
        );
      })}
    </div>
  );
}
