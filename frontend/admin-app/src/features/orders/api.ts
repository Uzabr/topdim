import api from '../../api/client';

export interface AdminOrder {
  id: number;
  userId: number;
  userEmail: string;
  userPhone: string;
  status: string;
  totalAmount: number;
  createdAt: string;
}

export interface AdminPurchasedCouponLookup {
  purchasedCouponId: number;
  orderId: number;
  userId: number;
  couponOfferId: number;
  couponOptionId: number;
  couponTitle: string;
  optionTitle: string;
  couponCode: string;
  status: string;
  merchantId: number;
  merchantName: string;
  merchantAddress: string;
  purchasedAt: string;
  expiresAt: string | null;
  usedAt: string | null;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function fetchAdminOrders(page = 0, size = 20, status?: string): Promise<PageResponse<AdminOrder>> {
  const params: Record<string, string | number> = { page, size };
  if (status) params.status = status;
  const res = await api.get('/api/v1/admin/orders', { params });
  return res.data.data;
}

export async function lookupPurchasedCoupon(couponCode: string): Promise<AdminPurchasedCouponLookup> {
  const res = await api.get('/api/v1/admin/purchased-coupons/lookup', {
    params: { couponCode: couponCode.trim().toUpperCase() },
  });
  return res.data.data;
}
