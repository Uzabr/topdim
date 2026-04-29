import api from '../../api/client';

export interface AdminReview {
  id: number;
  userId: number;
  userName: string;
  couponOfferId: number;
  rating: number;
  comment: string;
  status: string;
  rejectReason?: string;
  createdAt: string;
}

export interface AdminRefund {
  id: number;
  orderId: number;
  purchasedCouponId: number;
  userId: number;
  couponTitle: string;
  optionTitle?: string;
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

export interface AdminComplaint {
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

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ── Reviews ──

export async function getPendingReviews(page = 0, size = 20): Promise<PageResponse<AdminReview>> {
  const res = await api.get('/api/v1/mod/reviews', { params: { page, size } });
  return res.data.data;
}

export async function reviewUserReview(
  id: number,
  status: 'APPROVE' | 'REJECT',
  reason?: string
): Promise<void> {
  await api.patch(`/api/v1/mod/reviews/${id}/review`, { status, reason });
}

// ── Refunds ──

export async function getAdminRefunds(status?: string, page = 0, size = 20): Promise<PageResponse<AdminRefund>> {
  const res = await api.get('/api/v1/admin/refunds', { params: { status, page, size } });
  return res.data.data;
}

export async function approveRefund(id: number, adminComment?: string): Promise<AdminRefund> {
  const res = await api.patch(`/api/v1/admin/refunds/${id}/approve`, { adminComment });
  return res.data.data;
}

export async function rejectRefund(id: number, adminComment?: string): Promise<AdminRefund> {
  const res = await api.patch(`/api/v1/admin/refunds/${id}/reject`, { adminComment });
  return res.data.data;
}

export async function completeRefund(id: number, adminComment?: string): Promise<AdminRefund> {
  const res = await api.patch(`/api/v1/admin/refunds/${id}/complete`, { adminComment });
  return res.data.data;
}

// ── Complaints ──

export async function getPendingComplaints(page = 0, size = 20): Promise<PageResponse<AdminComplaint>> {
  const res = await api.get('/api/v1/mod/complaints', { params: { page, size } });
  return res.data.data;
}

export async function resolveComplaint(id: number, decision: 'RESOLVE' | 'REJECT', resolution: string): Promise<void> {
  await api.patch(`/api/v1/mod/complaints/${id}/resolve`, { decision, resolution });
}
