import apiClient from './client';
import type { ApiResponse } from './client';
import type { PagedResponse } from './orders';

export interface ComplaintData {
  id: number;
  userId: number;
  orderId: number;
  purchasedCouponId?: number;
  couponTitle?: string;
  optionTitle?: string;
  couponCode?: string;
  merchantName?: string;
  subject: string;
  description: string;
  status: 'PENDING' | 'IN_REVIEW' | 'RESOLVED' | 'REJECTED';
  resolution?: string;
  createdAt: string;
}

export const complaintsApi = {
  create: (data: { purchasedCouponId: number; subject: string; description: string }) =>
    apiClient.post<ApiResponse<number>>('/api/v1/complaints', data),

  getMine: (page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<ComplaintData>>>('/api/v1/complaints/my', {
      params: { page, size },
    }),
};
