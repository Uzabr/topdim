import apiClient from './client';
import type { ApiResponse } from './client';

// ═══ Types ═══

export interface Bazaar {
  id: number;
  name: string;
  nameUz?: string;
  type: 'BAZAAR' | 'SHOPPING_CENTER' | 'MARKET' | 'TRADE_COMPLEX';
  address?: string;
  city?: string;
  latitude: number;
  longitude: number;
  description?: string;
  coverImageUrl?: string;
  workingHours?: string;
  phone?: string;
  status: string;
  shopCount: number;
  isExternal?: boolean;
}

export interface Shop {
  id: number;
  name: string;
  description?: string;
  category?: string;
  subcategory?: string;
  goodsDescription?: string;
  phone?: string;
  workingHours?: string;
  photos: string[];
  locationType: 'BAZAAR' | 'STANDALONE';
  bazaar?: { id: number; name: string };
  pavilion?: string;
  sector?: string;
  rowNumber?: string;
  shopNumber?: string;
  floorNumber?: number;
  address?: string;
  latitude: number;
  longitude: number;
  status: string;
  isExternal?: boolean;
}

export interface AreaSearchResult {
  bazaars: Bazaar[];
  shops: Shop[];
}

// ═══ API Client ═══

export const directoryApi = {
  // Bazaars
  getBazaars: (params?: { search?: string; type?: string; page?: number; size?: number }) =>
    apiClient.get<{ data: Bazaar[]; totalPages: number; totalElements: number }>(
      '/api/v1/directory/bazaars', { params }
    ),

  getBazaarById: (id: number) =>
    apiClient.get<{ data: Bazaar }>(`/api/v1/directory/bazaars/${id}`),

  getShopsByBazaar: (bazaarId: number, search?: string) =>
    apiClient.get<{ data: Shop[] }>(`/api/v1/directory/bazaars/${bazaarId}/shops`, {
      params: search ? { search } : {},
    }),

  // Shops
  getShops: (params?: { search?: string; category?: string; page?: number; size?: number }) =>
    apiClient.get<{ data: Shop[]; totalPages: number; totalElements: number }>(
      '/api/v1/directory/shops', { params }
    ),

  getShopById: (id: number) =>
    apiClient.get<{ data: Shop }>(`/api/v1/directory/shops/${id}`),

  // Area search
  searchInArea: (bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }) =>
    apiClient.get<{ data: AreaSearchResult }>('/api/v1/directory/area', { params: bounds }),
};

// ═══ Legacy API (keep backward compat for existing pages) ═══

export const bazaarsApi = {
  getAll: (city?: string) =>
    apiClient.get<ApiResponse<Bazaar[]>>('/api/v1/directory/bazaars', { params: city ? { city } : {} }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<Bazaar>>(`/api/v1/directory/bazaars/${id}`),

  getShops: (bazaarId: number) =>
    apiClient.get<ApiResponse<Shop[]>>(`/api/v1/directory/bazaars/${bazaarId}/shops`),

  getShop: (shopId: number) =>
    apiClient.get<ApiResponse<Shop>>(`/api/v1/directory/shops/${shopId}`),

  searchShops: (query: string) =>
    apiClient.get<ApiResponse<Shop[]>>('/api/v1/directory/shops', { params: { search: query } }),
};
