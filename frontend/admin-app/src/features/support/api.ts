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

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

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
