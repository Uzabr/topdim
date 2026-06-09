import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { notificationsApi, type NotificationData } from '../../api/notifications';
import { Bell, Check } from 'lucide-react';
import './NotificationsSection.css';

export default function NotificationsSection() {
  const { t, i18n } = useTranslation();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const queryClient = useQueryClient();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';

  const { data, isLoading } = useQuery({
    queryKey: ['my-notifications', unreadOnly],
    queryFn: () => notificationsApi.getMine(unreadOnly || undefined),
    select: (res) => res.data.data,
  });

  const markRead = useMutation({
    mutationFn: (id: number) => notificationsApi.markRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['my-notifications'] });
    },
  });

  const notifications = data?.content ?? [];

  if (isLoading) return <div className="profile-loading">{t('profile.loadingNotifications')}</div>;

  return (
    <div className="notifications-section">
      <div className="notifications-filter">
        <label className="notifications-toggle">
          <input
            type="checkbox"
            checked={unreadOnly}
            onChange={(e) => setUnreadOnly(e.target.checked)}
          />
          <span>{t('profile.notifications.unreadOnly')}</span>
        </label>
      </div>

      {notifications.length === 0 ? (
        <div className="notifications-empty glass-card">
          <Bell size={32} />
          <h3>{t('profile.notifications.emptyTitle')}</h3>
          <p>{unreadOnly ? t('profile.notifications.allRead') : t('profile.notifications.empty')}</p>
        </div>
      ) : (
        <div className="notifications-list">
          {notifications.map((n: NotificationData) => (
            <div key={n.id} className={`notification-card glass-card ${n.read ? '' : 'notification-card--unread'}`}>
              <div className="notification-card__content">
                <h4>{n.title}</h4>
                <p>{n.message}</p>
                <span className="notification-card__date">
                  {new Date(n.createdAt).toLocaleDateString(locale, {
                    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
                  })}
                </span>
              </div>
              {!n.read && (
                <button
                  className="notification-card__mark"
                  onClick={() => markRead.mutate(n.id)}
                  title={t('profile.notifications.markRead')}
                >
                  <Check size={16} />
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
