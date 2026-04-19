import apiClient from './client';
import type { ApiResponse } from './client';

export interface CartItem {
  id: number;
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
  gift: boolean;
  giftRecipientName?: string;
  giftRecipientPhone?: string;
}

export interface Cart {
  id: number;
  userId: number;
  items: CartItem[];
  totalAmount: number;
  totalItems: number;
}

export interface AddToCartRequest {
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  unitPrice: number;
  quantity?: number;
  isGift?: boolean;
  giftRecipientName?: string;
  giftRecipientPhone?: string;
}

/**
 * OrderResponse DTO — aligned with backend OrderResponse.
 * Гарантированно содержит id, status, totalAmount.
 */
export interface OrderResponse {
  id: number;
  orderNumber: string;
  totalAmount: number;
  status: string;
  userEmail: string;
  userPhone: string;
  itemCount: number;
  createdAt: string;
  paidAt?: string;
}

export interface PurchasedCoupon {
  id: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  qrToken: string;
  status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'CANCELLED';
  purchasedAt: string;
  expiresAt?: string;
  usedAt?: string;
}

export const ordersApi = {
  getCart: () =>
    apiClient.get<ApiResponse<Cart>>('/api/v1/cart'),

  addToCart: (request: AddToCartRequest) =>
    apiClient.post<ApiResponse<Cart>>('/api/v1/cart/items', request),

  removeFromCart: (itemId: number) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/cart/items/${itemId}`),

  createOrder: (email: string, phone: string) =>
    apiClient.post<ApiResponse<OrderResponse>>('/api/v1/orders', { email, phone }),

  getOrder: (orderId: number) =>
    apiClient.get<ApiResponse<OrderResponse>>(`/api/v1/orders/${orderId}`),

  getMyCoupons: (status?: string) =>
    apiClient.get<ApiResponse<PurchasedCoupon[]>>('/api/v1/orders/my-coupons', {
      params: status ? { status } : {},
    }),
};
