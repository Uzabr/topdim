import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import {
  approveMerchantProfileChange,
  fetchMerchantProfileChangeDetail,
  fetchMerchantProfileChangeHistory,
  fetchModerationAssignees,
  fetchPublishedMerchantProfile,
  fetchMerchantProfileChanges,
  fetchPendingMerchantProfileChangeCount,
  reassignMerchantProfileChange,
  rejectMerchantProfileChange,
  releaseMerchantProfileChange,
  requestMerchantProfileChangeRevision,
  takeMerchantProfileChangeToWork,
} from './api';

vi.mock('../../api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}));

const emptyPage = {
  content: [],
  pageable: { pageNumber: 0, pageSize: 20 },
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

describe('merchant profile moderation API', () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset().mockResolvedValue({ data: { data: emptyPage } });
    vi.mocked(api.post).mockReset().mockResolvedValue({ data: { data: { id: 71 } } });
  });

  it('loads request detail and the published profile through role-compatible endpoints', async () => {
    vi.mocked(api.get)
      .mockResolvedValueOnce({ data: { data: { id: 71 } } })
      .mockResolvedValueOnce({ data: { data: [{ id: 9, name: 'Safia' }] } })
      .mockResolvedValueOnce({ data: { data: { id: 9, name: 'Safia' } } });

    await fetchMerchantProfileChangeDetail(71);
    await fetchPublishedMerchantProfile(9, 'MODERATOR');
    await fetchPublishedMerchantProfile(9, 'ADMIN');

    expect(api.get).toHaveBeenNthCalledWith(
      1,
      '/api/v1/admin/merchant-change-requests/71',
    );
    expect(api.get).toHaveBeenNthCalledWith(2, '/api/v1/admin/merchants');
    expect(api.get).toHaveBeenNthCalledWith(3, '/api/v1/admin/merchants/9');
  });

  it('loads the full request history', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({ data: { data: [{ id: 1 }] } });

    await fetchMerchantProfileChangeHistory(71);

    expect(api.get).toHaveBeenCalledWith(
      '/api/v1/admin/merchant-change-requests/71/history',
    );
  });

  it('loads eligible moderation assignees for the selector', async () => {
    vi.mocked(api.get).mockResolvedValueOnce({
      data: { data: [{ userId: 88, name: 'Ali Valiyev', role: 'MODERATOR' }] },
    });

    await expect(fetchModerationAssignees()).resolves.toEqual([
      { userId: 88, name: 'Ali Valiyev', role: 'MODERATOR' },
    ]);
    expect(api.get).toHaveBeenCalledWith(
      '/api/v1/admin/merchant-change-requests/assignees',
    );
  });

  it('sends exact moderation action methods and normalized payloads', async () => {
    await approveMerchantProfileChange(71);
    await requestMerchantProfileChangeRevision(71, '  Уточните адрес  ');
    await rejectMerchantProfileChange(71, '  Нарушение правил  ');
    await releaseMerchantProfileChange(71);
    await reassignMerchantProfileChange(71, 88);

    expect(api.post).toHaveBeenNthCalledWith(
      1,
      '/api/v1/admin/merchant-change-requests/71/approve',
    );
    expect(api.post).toHaveBeenNthCalledWith(
      2,
      '/api/v1/admin/merchant-change-requests/71/request-revision',
      { comment: 'Уточните адрес' },
    );
    expect(api.post).toHaveBeenNthCalledWith(
      3,
      '/api/v1/admin/merchant-change-requests/71/reject',
      { comment: 'Нарушение правил' },
    );
    expect(api.post).toHaveBeenNthCalledWith(
      4,
      '/api/v1/admin/merchant-change-requests/71/release',
    );
    expect(api.post).toHaveBeenNthCalledWith(
      5,
      '/api/v1/admin/merchant-change-requests/71/reassign',
      { assigneeUserId: 88 },
    );
  });

  it('sends normalized queue filters with server pagination', async () => {
    await fetchMerchantProfileChanges({
      status: 'IN_REVIEW',
      search: '  Safia  ',
      assigneeUserId: 42,
      submittedFrom: '2026-08-01T00:00:00',
      submittedTo: '2026-08-13T23:59:59',
      page: 3,
      size: 50,
    });

    expect(api.get).toHaveBeenCalledWith('/api/v1/admin/merchant-change-requests', {
      params: {
        status: 'IN_REVIEW',
        search: 'Safia',
        assigneeUserId: 42,
        submittedFrom: '2026-08-01T00:00:00',
        submittedTo: '2026-08-13T23:59:59',
        page: 3,
        size: 50,
      },
    });
  });

  it('omits blank and empty optional filters', async () => {
    await fetchMerchantProfileChanges({
      search: '   ',
      page: 0,
      size: 20,
    });

    expect(api.get).toHaveBeenCalledWith('/api/v1/admin/merchant-change-requests', {
      params: { page: 0, size: 20 },
    });
  });

  it('loads the pending count from the full server result, not the visible page', async () => {
    vi.mocked(api.get).mockResolvedValue({
      data: { data: { ...emptyPage, totalElements: 37 } },
    });

    await expect(fetchPendingMerchantProfileChangeCount()).resolves.toBe(37);
    expect(api.get).toHaveBeenCalledWith('/api/v1/admin/merchant-change-requests', {
      params: { status: 'PENDING_REVIEW', page: 0, size: 1 },
    });
  });

  it('claims a pending request through the dedicated transition endpoint', async () => {
    await takeMerchantProfileChangeToWork(71);

    expect(api.post).toHaveBeenCalledWith(
      '/api/v1/admin/merchant-change-requests/71/take-to-work',
    );
  });
});
