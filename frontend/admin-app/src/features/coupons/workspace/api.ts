import api from '../../../api/client';
import type { ApiResponse, PageResponse } from '../../../types';
import { workspaceStatuses } from './state';
import type { AdminCouponRow, CouponWorkspaceState } from './types';

export const ADMIN_COUPONS_WORKSPACE_QUERY_KEY = [
  'admin-coupons',
  'workspace',
] as const;

export async function fetchAdminCoupons(
  state: CouponWorkspaceState,
  pageParam = state.page,
): Promise<PageResponse<AdminCouponRow>> {
  const statuses = workspaceStatuses(state.tab);
  const params: Record<string, string | number> = {
    page: pageParam,
    size: state.pageSize,
  };

  if (statuses.length === 1) {
    params.status = statuses[0];
  } else {
    params.statuses = statuses.join(',');
  }

  const search = state.search.trim();
  if (search) {
    params.search = search;
  }
  if (state.merchantId !== null) {
    params.merchantId = state.merchantId;
  }
  if (state.assignedModeratorId !== null) {
    params.assignedModeratorId = state.assignedModeratorId;
  }

  const response = await api.get<ApiResponse<PageResponse<AdminCouponRow>>>(
    '/api/v1/admin/coupons',
    { params },
  );
  return response.data.data;
}
