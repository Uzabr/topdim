import apiClient from './client';
import type { ApiResponse } from './client';

export interface Bazaar {
  id: number;
  name: string;
  nameUz?: string;
  type: 'BAZAAR' | 'SHOPPING_CENTER' | 'MARKET' | 'TRADE_COMPLEX';
  address?: string;
  city?: string;
  latitude?: number;
  longitude?: number;
  description?: string;
  coverImageUrl?: string;
  workingHours?: string;
  phone?: string;
}

export interface BazaarMap {
  id: number;
  floorNumber: number;
  floorName?: string;
  mapImageUrl: string;
  mapSvgUrl?: string;
  zonesJson?: string;
}

export interface Shop {
  id: number;
  bazaar: { id: number; name: string };
  name: string;
  rowNumber?: string;
  shopNumber?: string;
  category?: { id: number; name: string };
  goodsDescription?: string;
  workingHours?: string;
  phone?: string;
  photoUrl?: string;
  hasCoupon: boolean;
  linkedCouponOfferId?: number;
  floorNumber: number;
  zoneId?: string;
  productTags: { tag: string; tagUz?: string }[];
}

export const bazaarsApi = {
  getAll: (city?: string) =>
    apiClient.get<ApiResponse<Bazaar[]>>('/api/v1/bazaars', { params: city ? { city } : {} }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<Bazaar>>(`/api/v1/bazaars/${id}`),

  getMaps: (bazaarId: number) =>
    apiClient.get<ApiResponse<BazaarMap[]>>(`/api/v1/bazaars/${bazaarId}/map`),

  getShops: (bazaarId: number, hasCoupon?: boolean) =>
    apiClient.get<ApiResponse<Shop[]>>(`/api/v1/bazaars/${bazaarId}/shops`, {
      params: hasCoupon !== undefined ? { hasCoupon } : {},
    }),

  getShop: (shopId: number) =>
    apiClient.get<ApiResponse<Shop>>(`/api/v1/shops/${shopId}`),

  searchShops: (query: string) =>
    apiClient.get<ApiResponse<Shop[]>>('/api/v1/shops/search', { params: { q: query } }),
};
