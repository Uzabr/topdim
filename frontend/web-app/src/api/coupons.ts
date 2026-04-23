import apiClient from './client';
import type { ApiResponse } from './client';

export interface MerchantPrimaryLocation {
  id: number;
  title?: string;
  address?: string;
  phone?: string;
  workingHours?: string;
  latitude?: number;
  longitude?: number;
}

export interface CouponOffer {
  id: number;
  title: string;
  /** Canonical offer description (Release 1+). */
  offerDescription?: string;
  merchant: {
    id: number;
    name: string;
    logoUrl?: string;
    description?: string;
    primaryLocation?: MerchantPrimaryLocation;
  };
  category: { id: number; name: string; slug: string; iconUrl?: string };
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  buyUntil: string;
  useUntil: string;
  giftAvailable: boolean;
  status: string;
  totalSold: number;
  viewCount: number;
  averageRating?: number;
  reviewCount?: number;
  options: CouponOption[];
  images: string[];
  createdAt: string;
}

export interface CouponOption {
  id: number;
  title: string;
  regularPrice: number;
  couponPrice: number;
  quantityLimit?: number;
  quantitySold: number;
  status: string;
}

export interface Category {
  id: number;
  name: string;
  nameUz?: string;
  slug: string;
  iconUrl?: string;
  sortOrder: number;
}

interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
}

export interface CouponListParams {
  categoryId?: number;
  search?: string;
  sortBy?: string;
  page?: number;
  size?: number;
}

export const couponsApi = {
  getCatalog: (params: CouponListParams = {}) =>
    apiClient.get<ApiResponse<PagedResponse<CouponOffer>>>('/api/v1/coupons', { params }),

  getById: (id: number) =>
    apiClient.get<ApiResponse<CouponOffer>>(`/api/v1/coupons/${id}`),

  getTopSelling: (limit: number = 10) =>
    apiClient.get<ApiResponse<CouponOffer[]>>('/api/v1/coupons/top-selling', { params: { limit } }),

  getCategories: () =>
    apiClient.get<ApiResponse<Category[]>>('/api/v1/categories'),
};
