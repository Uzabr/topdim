import api from '../../api/client';
import type { ApiResponse } from '../../types';
import type { UserRole } from '../../types';
import type {
  MerchantProfileChangeDetail,
  MerchantProfileChangePage,
  MerchantProfileChangeQueueFilters,
  PublishedMerchantProfile,
  ModerationAssigneeOption,
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

export async function fetchMerchantProfileChangeDetail(
  id: number,
): Promise<MerchantProfileChangeDetail> {
  const response = await api.get<ApiResponse<MerchantProfileChangeDetail>>(`${ENDPOINT}/${id}`);
  return response.data.data;
}

export async function fetchPublishedMerchantProfile(
  merchantId: number,
  role: UserRole,
): Promise<PublishedMerchantProfile> {
  if (role === 'MODERATOR') {
    const response = await api.get<ApiResponse<PublishedMerchantProfile[]>>(
      '/api/v1/admin/merchants',
    );
    const merchant = response.data.data.find((item) => item.id === merchantId);
    if (!merchant) throw new Error('Опубликованный профиль компании не найден');
    return merchant;
  }
  const response = await api.get<ApiResponse<PublishedMerchantProfile>>(
    `/api/v1/admin/merchants/${merchantId}`,
  );
  return response.data.data;
}

export async function approveMerchantProfileChange(id: number): Promise<void> {
  await api.post(`${ENDPOINT}/${id}/approve`);
}

export async function requestMerchantProfileChangeRevision(
  id: number,
  comment: string,
): Promise<void> {
  await api.post(`${ENDPOINT}/${id}/request-revision`, { comment: comment.trim() });
}

export async function rejectMerchantProfileChange(id: number, comment: string): Promise<void> {
  await api.post(`${ENDPOINT}/${id}/reject`, { comment: comment.trim() });
}

export async function releaseMerchantProfileChange(id: number): Promise<void> {
  await api.post(`${ENDPOINT}/${id}/release`);
}

export async function reassignMerchantProfileChange(
  id: number,
  assigneeUserId: number,
): Promise<void> {
  await api.post(`${ENDPOINT}/${id}/reassign`, { assigneeUserId });
}

export async function fetchModerationAssignees(): Promise<ModerationAssigneeOption[]> {
  const response = await api.get<ApiResponse<ModerationAssigneeOption[]>>(
    `${ENDPOINT}/assignees`,
  );
  return response.data.data;
}
