import apiClient from './client';
import type { ApiResponse } from './client';
import type { PagedResponse } from './orders';

export interface NotificationData {
  id: number;
  title: string;
  message: string;
  type: string;
  read: boolean;
  createdAt: string;
}

export const notificationsApi = {
  getMine: (unreadOnly?: boolean, page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<NotificationData>>>('/api/v1/notifications', {
      params: { unreadOnly, page, size },
    }),

  markRead: (id: number) =>
    apiClient.patch<ApiResponse<string>>(`/api/v1/notifications/${id}/read`),
};
