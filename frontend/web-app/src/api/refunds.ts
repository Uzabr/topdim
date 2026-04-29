import apiClient from './client';
import type { ApiResponse } from './client';

export interface RefundRequestData {
  id: number;
  orderId: number;
  purchasedCouponId: number;
  userId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  merchantName?: string;
  refundAmount?: number;
  reason: string;
  status: 'PENDING' | 'APPROVED_PROCESSING' | 'REFUNDED' | 'REJECTED';
  adminComment?: string;
  createdAt: string;
  resolvedAt?: string;
  expectedRefundAt?: string;
  completedAt?: string;
}

export const refundsApi = {
  create: (data: { purchasedCouponId: number; reason: string }) =>
    apiClient.post<ApiResponse<RefundRequestData>>('/api/v1/refunds', data),

  getMine: () =>
    apiClient.get<ApiResponse<RefundRequestData[]>>('/api/v1/refunds/my'),
};
