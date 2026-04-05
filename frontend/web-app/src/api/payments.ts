import apiClient from './client';
import type { ApiResponse } from './client';

export interface CreatePaymentRequest {
  orderId: number;
  amount: number;
  provider: 'CARD' | 'CLICK' | 'PAYME';
}

export interface PaymentResponse {
  id: number;
  orderId: number;
  amount: number;
  provider: string;
  status: string;
  redirectUrl?: string;
  createdAt: string;
}

export const paymentsApi = {
  createPayment: (data: CreatePaymentRequest) =>
    apiClient.post<ApiResponse<PaymentResponse>>('/api/v1/payments/create', data),

  getStatus: (paymentId: number) =>
    apiClient.get<ApiResponse<PaymentResponse>>(`/api/v1/payments/${paymentId}/status`),
};
