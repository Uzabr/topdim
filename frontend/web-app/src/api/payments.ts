import apiClient from './client';
import type { ApiResponse } from './client';

export interface CreatePaymentRequest {
  orderId: number;
  amount: number;
  provider: 'CARD' | 'CLICK' | 'PAYME';
}

/**
 * PaymentResponse DTO — aligned with backend PaymentResponse.
 * Field names match backend: statusName, paymentUrl, paymentMode.
 */
export interface PaymentResponse {
  id: number;
  orderId: number;
  userId: number;
  amount: number;
  currency: string;
  provider: string;
  statusName: string;
  transactionId?: string;
  paymentUrl?: string;
  /** Режим оплаты: "demo" (без реальной ПС) или "provider" (реальная ПС) */
  paymentMode?: 'demo' | 'provider';
  createdAt: string;
  completedAt?: string;
}

export const paymentsApi = {
  createPayment: (data: CreatePaymentRequest) =>
    apiClient.post<ApiResponse<PaymentResponse>>('/api/v1/payments/create', data),

  getStatus: (paymentId: number) =>
    apiClient.get<ApiResponse<PaymentResponse>>(`/api/v1/payments/${paymentId}/status`),

  /**
   * Получить платёж по orderId.
   * Основной endpoint для storefront polling после создания order.
   * Возвращает 404 если payment ещё не создан (event-driven задержка).
   */
  getByOrderId: (orderId: number) =>
    apiClient.get<ApiResponse<PaymentResponse>>(`/api/v1/payments/order/${orderId}`),

  /**
   * Демо-подтверждение оплаты (только в demo mode).
   * Завершает payment без реального провайдера.
   * Идемпотентен: повторные вызовы безопасны.
   */
  demoComplete: (orderId: number) =>
    apiClient.post<ApiResponse<PaymentResponse>>(`/api/v1/payments/order/${orderId}/demo-complete`),
};
