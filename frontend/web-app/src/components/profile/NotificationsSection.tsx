import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { notificationsApi, type NotificationData } from '../../api/notifications';
import { Bell, Check } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import { profileQueryKeys } from '../../queries/profileQueries';
import './NotificationsSection.css';

export default function NotificationsSection() {
  const { t, i18n } = useTranslation();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const queryClient = useQueryClient();
  const userId = useAuthStore((state) => state.user?.id) ?? 0;
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';

  const {
    data: notifications = [],
    isLoading,
    isLoadingError,
    isFetchNextPageError,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useInfiniteQuery({
    queryKey: profileQueryKeys.notifications(userId, unreadOnly),
    queryFn: ({ pageParam }) =>
      notificationsApi.getMine(unreadOnly || undefined, pageParam, 20),
    initialPageParam: 0,
    getNextPageParam: (lastPage) =>
      lastPage.data.data.last ? undefined : lastPage.data.data.number + 1,
    select: (result) =>
      result.pages.flatMap((page) => page.data.data.content),
    enabled: userId !== 0,
  });

  const markRead = useMutation({
    mutationFn: (id: number) => notificationsApi.markRead(id),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: profileQueryKeys.notificationsRoot(userId),
      });
    },
  });

  if (isLoading) return <div className="profile-loading">{t('profile.loadingNotifications')}</div>;

  if (isLoadingError) {
    return (
      <div className="notifications-error surface-card" role="alert">
        <p>{t('profile.notifications.error')}</p>
        <button
          type="button"
          className="notifications-action"
          onClick={() => void refetch()}
        >
          {t('profile.notifications.retry')}
        </button>
      </div>
    );
  }

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
        <div className="notifications-empty surface-card">
          <Bell size={32} />
          <h3>{t('profile.notifications.emptyTitle')}</h3>
          <p>{unreadOnly ? t('profile.notifications.allRead') : t('profile.notifications.empty')}</p>
        </div>
      ) : (
        <>
          <div className="notifications-list">
            {notifications.map((n: NotificationData) => (
              <div key={n.id} className={`notification-card surface-card ${n.read ? '' : 'notification-card--unread'}`}>
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
          {isFetchNextPageError ? (
            <div className="notifications-pagination-error" role="alert">
              <p>{t('profile.notifications.loadMoreError')}</p>
              <button
                type="button"
                className="notifications-action"
                disabled={isFetchingNextPage}
                onClick={() => void fetchNextPage()}
              >
                {isFetchingNextPage
                  ? t('profile.loadingNotifications')
                  : t('profile.notifications.retry')}
              </button>
            </div>
          ) : hasNextPage && (
            <button
              type="button"
              className="notifications-load-more"
              disabled={isFetchingNextPage}
              onClick={() => void fetchNextPage()}
            >
              {isFetchingNextPage
                ? t('profile.loadingNotifications')
                : t('profile.notifications.loadMore')}
            </button>
          )}
        </>
      )}
    </div>
  );
}
