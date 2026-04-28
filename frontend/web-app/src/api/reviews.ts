import apiClient from './client';
import type { ApiResponse } from './client';

export interface ReviewData {
  id: number;
  userId: number;
  userName?: string;
  couponOfferId: number;
  rating: number;
  comment: string;
  status: string;
  createdAt: string;
}

export interface CreateReviewRequest {
  couponOfferId: number;
  rating: number;
  comment: string;
}

export interface ReviewEligibilityData {
  eligible: boolean;
  reason?: string;
  purchasedCouponId?: number;
  usedAt?: string;
}

interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
}

export const reviewsApi = {
  getForCoupon: (couponId: number, page = 0, size = 10) =>
    apiClient.get<ApiResponse<PagedResponse<ReviewData>>>(`/api/v1/reviews/coupon/${couponId}`, {
      params: { page, size },
    }),

  create: (data: CreateReviewRequest) =>
    apiClient.post<ApiResponse<number>>('/api/v1/reviews', data),

  getMine: (page = 0, size = 10) =>
    apiClient.get<ApiResponse<PagedResponse<ReviewData>>>('/api/v1/reviews/my', {
      params: { page, size },
    }),

  getEligibility: (couponId: number) =>
    apiClient.get<ApiResponse<ReviewEligibilityData>>(`/api/v1/reviews/coupon/${couponId}/eligibility`),
};
