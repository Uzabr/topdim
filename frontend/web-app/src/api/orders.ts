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
  /**
   * TODO(backend): в макете строка заказа ведётся названием оффера, а не «Заказ №».
   * OrderResponse его пока не отдаёт (заказ — набор из itemCount позиций).
   * Добавить представительное название (первая позиция + «и ещё N») в order-service
   * OrderResponse. Пока поля нет — UI показывает «Заказ №{orderNumber}».
   */
  title?: string;
}

export interface PurchasedCoupon {
  id: number;
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  qrToken: string;
  status: 'ACTIVE' | 'USED' | 'EXPIRED' | 'REFUND_PENDING' | 'REFUNDED' | 'CANCELLED';
  merchantId?: number;
  merchantName?: string;
  merchantAddress?: string;
  merchantPhone?: string;
  merchantWorkingHours?: string;
  purchasedAt: string;
  expiresAt?: string;
  usedAt?: string;
  refundRequestId?: number;
  refundStatus?: string;
  refundExpectedAt?: string;
  /**
   * TODO(backend): в макете тикет показывает уплаченную цену («49 000 сум»), а не название опции.
   * order-service знает цену позиции на момент покупки (unitPrice) — отдать её в my-coupons.
   * Пока поля нет — тикет показывает optionTitle (реальные данные, не фейк).
   */
  pricePaid?: number;
}

export interface PagedResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
  last: boolean;
}

export const ordersApi = {
  getCart: () =>
    apiClient.get<ApiResponse<Cart>>('/api/v1/cart'),

  addToCart: (request: AddToCartRequest) =>
    apiClient.post<ApiResponse<Cart>>('/api/v1/cart/items', request),

  removeFromCart: (itemId: number) =>
    apiClient.delete<ApiResponse<void>>(`/api/v1/cart/items/${itemId}`),

  updateCartItemQuantity: (itemId: number, quantity: number) =>
    apiClient.patch<ApiResponse<Cart>>(`/api/v1/cart/items/${itemId}`, { quantity }),

  createOrder: (email: string, phone: string) =>
    apiClient.post<ApiResponse<OrderResponse>>('/api/v1/orders', { email, phone }),

  getOrder: (orderId: number) =>
    apiClient.get<ApiResponse<OrderResponse>>(`/api/v1/orders/${orderId}`),

  getOrders: (page = 0, size = 20) =>
    apiClient.get<ApiResponse<PagedResponse<OrderResponse>>>('/api/v1/orders', {
      params: { page, size },
    }),

  getMyCoupons: (status?: string) =>
    apiClient.get<ApiResponse<PurchasedCoupon[]>>('/api/v1/orders/my-coupons', {
      params: status ? { status } : {},
    }),
};

