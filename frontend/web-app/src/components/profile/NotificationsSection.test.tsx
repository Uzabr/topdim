// @vitest-environment jsdom
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { notificationsApi, type NotificationData } from '../../api/notifications';
import { profileQueryKeys } from '../../queries/profileQueries';
import NotificationsSection from './NotificationsSection';

vi.mock('react-i18next', () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { language: 'ru' },
  }),
}));

vi.mock('../../api/notifications', () => ({
  notificationsApi: {
    getMine: vi.fn(),
    markRead: vi.fn(),
  },
}));

vi.mock('../../store/authStore', () => ({
  useAuthStore: (selector: (state: { user: { id: number } }) => unknown) =>
    selector({ user: { id: 7 } }),
}));

const makeNotification = (id: number, read = false): NotificationData => ({
  id,
  title: `Notification ${id}`,
  message: `Message ${id}`,
  type: 'SYSTEM',
  read,
  createdAt: '2026-07-25T10:00:00Z',
});

const makePage = (
  content: NotificationData[],
  number = 0,
  last = true,
) => ({
  data: {
    success: true,
    data: {
      content,
      totalElements: content.length,
      totalPages: last ? number + 1 : number + 2,
      size: 20,
      number,
      last,
    },
    timestamp: '2026-07-25T10:00:00Z',
  },
});

function renderNotifications() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });

  const view = render(
    <QueryClientProvider client={queryClient}>
      <NotificationsSection />
    </QueryClientProvider>,
  );

  return { ...view, queryClient };
}

describe('NotificationsSection', () => {
  afterEach(cleanup);

  beforeEach(() => {
    vi.mocked(notificationsApi.getMine).mockReset();
    vi.mocked(notificationsApi.markRead).mockReset();
  });

  it('shows a loading state while notifications are pending', async () => {
    let resolveNotifications!: (value: ReturnType<typeof makePage>) => void;
    const pendingNotifications = new Promise<ReturnType<typeof makePage>>(
      (resolve) => {
        resolveNotifications = resolve;
      },
    );
    vi.mocked(notificationsApi.getMine).mockReturnValue(
      pendingNotifications as ReturnType<typeof notificationsApi.getMine>,
    );

    renderNotifications();

    expect(screen.getByText('profile.loadingNotifications')).toBeTruthy();

    await act(async () => {
      resolveNotifications(makePage([]));
    });
  });

  it('shows an explicit error and retries without presenting an empty state', async () => {
    vi.mocked(notificationsApi.getMine)
      .mockRejectedValueOnce(new Error('network unavailable'))
      .mockResolvedValueOnce(makePage([]) as Awaited<ReturnType<typeof notificationsApi.getMine>>);
    renderNotifications();

    expect((await screen.findByRole('alert')).textContent).toContain(
      'profile.notifications.error',
    );
    expect(screen.queryByText('profile.notifications.emptyTitle')).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: 'profile.notifications.retry' }));

    await waitFor(() => expect(notificationsApi.getMine).toHaveBeenCalledTimes(2));
    expect(await screen.findByText('profile.notifications.emptyTitle')).toBeTruthy();
  });

  it('shows the empty state only after an empty response succeeds', async () => {
    vi.mocked(notificationsApi.getMine).mockResolvedValue(
      makePage([]) as Awaited<ReturnType<typeof notificationsApi.getMine>>,
    );
    const { queryClient } = renderNotifications();

    expect(await screen.findByText('profile.notifications.emptyTitle')).toBeTruthy();
    expect(screen.queryByRole('alert')).toBeNull();
    expect(queryClient.getQueryCache().find({
      queryKey: profileQueryKeys.notifications(7, false),
    })).toBeDefined();
  });

  it('requests only unread notifications when the filter is enabled', async () => {
    vi.mocked(notificationsApi.getMine).mockResolvedValue(
      makePage([]) as Awaited<ReturnType<typeof notificationsApi.getMine>>,
    );
    renderNotifications();

    await waitFor(() => {
      expect(notificationsApi.getMine).toHaveBeenCalledWith(undefined, 0, 20);
    });
    await screen.findByText('profile.notifications.emptyTitle');
    fireEvent.click(screen.getByRole('checkbox', {
      name: 'profile.notifications.unreadOnly',
    }));

    await waitFor(() => {
      expect(notificationsApi.getMine).toHaveBeenCalledWith(true, 0, 20);
    });
    expect(await screen.findByText('profile.notifications.allRead')).toBeTruthy();
  });

  it('invalidates all notification pages after marking one as read', async () => {
    vi.mocked(notificationsApi.getMine).mockResolvedValue(
      makePage([makeNotification(7)]) as Awaited<ReturnType<typeof notificationsApi.getMine>>,
    );
    vi.mocked(notificationsApi.markRead).mockResolvedValue(
      {} as Awaited<ReturnType<typeof notificationsApi.markRead>>,
    );
    const { queryClient } = renderNotifications();
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');

    fireEvent.click(await screen.findByTitle('profile.notifications.markRead'));

    await waitFor(() => expect(notificationsApi.markRead).toHaveBeenCalledWith(7));
    expect(invalidateQueries).toHaveBeenCalledWith({
      queryKey: ['my-notifications', 7],
    });
  });

  it('loads notifications in 20-item pages and appends the next page', async () => {
    const firstPage = Array.from({ length: 20 }, (_, index) =>
      makeNotification(index + 1),
    );
    vi.mocked(notificationsApi.getMine).mockImplementation(
      (_unreadOnly, page) => Promise.resolve(
        page === 0
          ? makePage(firstPage, 0, false)
          : makePage([makeNotification(21)], 1, true),
      ) as ReturnType<typeof notificationsApi.getMine>,
    );
    renderNotifications();

    expect(await screen.findByText('Notification 1')).toBeTruthy();
    expect(notificationsApi.getMine).toHaveBeenCalledWith(undefined, 0, 20);

    fireEvent.click(screen.getByRole('button', {
      name: 'profile.notifications.loadMore',
    }));

    expect(await screen.findByText('Notification 21')).toBeTruthy();
    expect(notificationsApi.getMine).toHaveBeenCalledWith(undefined, 1, 20);
    expect(screen.queryByRole('button', {
      name: 'profile.notifications.loadMore',
    })).toBeNull();
  });

  it('retains page 0 and disables the next-page retry while recovery is pending', async () => {
    let pageOneAttempts = 0;
    let resolveRetry!: (value: ReturnType<typeof makePage>) => void;
    const retryResponse = new Promise<ReturnType<typeof makePage>>((resolve) => {
      resolveRetry = resolve;
    });
    vi.mocked(notificationsApi.getMine).mockImplementation(
      (_unreadOnly, page) => {
        if (page === 0) {
          return Promise.resolve(
            makePage([makeNotification(1)], 0, false),
          ) as ReturnType<typeof notificationsApi.getMine>;
        }
        pageOneAttempts += 1;
        if (pageOneAttempts === 1) {
          return Promise.reject(new Error('page 1 unavailable'));
        }
        return retryResponse as ReturnType<typeof notificationsApi.getMine>;
      },
    );
    renderNotifications();

    expect(await screen.findByText('Notification 1')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', {
      name: 'profile.notifications.loadMore',
    }));

    expect((await screen.findByRole('alert')).textContent).toContain(
      'profile.notifications.loadMoreError',
    );
    expect(screen.getByText('Notification 1')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', {
      name: 'profile.notifications.retry',
    }));

    const retry = await screen.findByRole('button', {
      name: 'profile.loadingNotifications',
    });
    expect((retry as HTMLButtonElement).disabled).toBe(true);

    await act(async () => {
      resolveRetry(makePage([makeNotification(21)], 1, true));
    });
    expect(await screen.findByText('Notification 21')).toBeTruthy();
  });
});
