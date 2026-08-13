import api from '../../api/client';
import type { ApiResponse } from '../../types';
import type {
  MerchantProfileChangePage,
  MerchantProfileChangeQueueFilters,
} from './types';

const ENDPOINT = '/api/v1/admin/merchant-change-requests';

export const MERCHANT_PROFILE_CHANGES_QUERY_KEY = ['merchant-profile-changes'] as const;

export async function fetchMerchantProfileChanges(
  filters: MerchantProfileChangeQueueFilters,
): Promise<MerchantProfileChangePage> {
  const search = filters.search?.trim();
  const response = await api.get<ApiResponse<MerchantProfileChangePage>>(ENDPOINT, {
    params: {
      ...(filters.status ? { status: filters.status } : {}),
      ...(search ? { search } : {}),
      ...(filters.assigneeUserId ? { assigneeUserId: filters.assigneeUserId } : {}),
      ...(filters.submittedFrom ? { submittedFrom: filters.submittedFrom } : {}),
      ...(filters.submittedTo ? { submittedTo: filters.submittedTo } : {}),
      page: filters.page,
      size: filters.size,
    },
  });
  return response.data.data;
}

export async function fetchPendingMerchantProfileChangeCount(): Promise<number> {
  const page = await fetchMerchantProfileChanges({
    status: 'PENDING_REVIEW',
    page: 0,
    size: 1,
  });
  return page.totalElements;
}

export async function takeMerchantProfileChangeToWork(
  id: number,
): Promise<void> {
  await api.post(`${ENDPOINT}/${id}/take-to-work`);
}
