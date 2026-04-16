import apiClient from './client';
import type { ApiResponse } from './client';

export interface CouponOffer {
  id: number;
  title: string;
  shortDescription?: string;
  fullDescription?: string;
  merchant: { id: number; name: string; logoUrl?: string };
  category: { id: number; name: string; slug: string; iconUrl?: string };
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  buyUntil: string;
  useUntil: string;
  terms?: string;
  usageRules?: string;
  howToUse?: string;
  address?: string;
  contactPhone?: string;
  workingHours?: string;
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
