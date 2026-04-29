import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { notificationsApi, type NotificationData } from '../../api/notifications';
import { Bell, Check } from 'lucide-react';
import './NotificationsSection.css';

export default function NotificationsSection() {
  const [unreadOnly, setUnreadOnly] = useState(false);
  const queryClient = useQueryClient();

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

  if (isLoading) return <div className="profile-loading">Загрузка уведомлений...</div>;

  return (
    <div className="notifications-section">
      <div className="notifications-filter">
        <label className="notifications-toggle">
          <input
            type="checkbox"
            checked={unreadOnly}
            onChange={(e) => setUnreadOnly(e.target.checked)}
          />
          <span>Только непрочитанные</span>
        </label>
      </div>

      {notifications.length === 0 ? (
        <div className="notifications-empty glass-card">
          <Bell size={32} />
          <h3>Нет уведомлений</h3>
          <p>{unreadOnly ? 'Все уведомления прочитаны.' : 'Здесь будут ваши уведомления.'}</p>
        </div>
      ) : (
        <div className="notifications-list">
          {notifications.map((n: NotificationData) => (
            <div key={n.id} className={`notification-card glass-card ${n.read ? '' : 'notification-card--unread'}`}>
              <div className="notification-card__content">
                <h4>{n.title}</h4>
                <p>{n.message}</p>
                <span className="notification-card__date">
                  {new Date(n.createdAt).toLocaleDateString('ru-RU', {
                    day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
                  })}
                </span>
              </div>
              {!n.read && (
                <button
                  className="notification-card__mark"
                  onClick={() => markRead.mutate(n.id)}
                  title="Отметить как прочитанное"
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
