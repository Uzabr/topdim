import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';

export interface DailySales {
  date: string;
  orders: number;
  revenue: number;
}

export interface RecentOrder {
  id: number;
  orderNumber: string;
  userEmail: string | null;
  status: string;
  totalAmount: number;
  createdAt: string;
}

export interface AdminDashboardData {
  ordersToday: number;
  paidRevenueToday: number;
  pendingComplaints: number;
  salesLast7Days: DailySales[];
  recentOrders: RecentOrder[];
}

export async function fetchAdminDashboard(): Promise<AdminDashboardData> {
  const response = await api.get<ApiResponse<AdminDashboardData>>(
    '/api/v1/admin/dashboard',
  );
  return response.data.data;
}

export async function fetchCouponCount(params: {
  status?: string;
  statuses?: string;
  assignedModeratorId?: number;
}): Promise<number> {
  const response = await api.get<ApiResponse<PageResponse<unknown>>>(
    '/api/v1/admin/coupons',
    { params: { ...params, page: 0, size: 1 } },
  );
  return response.data.data.totalElements;
}

export async function fetchAdminUserCount(): Promise<number> {
  const response = await api.get<ApiResponse<PageResponse<unknown>>>(
    '/api/v1/admin/users',
    { params: { page: 0, size: 1 } },
  );
  return response.data.data.totalElements;
}

export async function fetchPendingComplaintCount(): Promise<number> {
  const response = await api.get<ApiResponse<PageResponse<unknown>>>(
    '/api/v1/mod/complaints',
    { params: { page: 0, size: 1 } },
  );
  return response.data.data.totalElements;
}
