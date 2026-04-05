import apiClient from './client';
import type { ApiResponse } from './client';

export interface FavoriteItem {
  id: number;
  couponOfferId: number;
  createdAt: string;
}

export const favoritesApi = {
  getAll: () =>
    apiClient.get<ApiResponse<FavoriteItem[]>>('/api/v1/users/me/favorites'),

  add: (couponOfferId: number) =>
    apiClient.post<ApiResponse<FavoriteItem>>('/api/v1/users/me/favorites', { couponOfferId }),

  remove: (couponOfferId: number) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/users/me/favorites/${couponOfferId}`),
};
