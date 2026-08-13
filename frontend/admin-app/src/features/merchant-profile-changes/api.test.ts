import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import {
  fetchMerchantProfileChanges,
  fetchPendingMerchantProfileChangeCount,
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
    vi.mocked(api.get).mockResolvedValue({ data: { data: emptyPage } });
    vi.mocked(api.post).mockResolvedValue({ data: { data: { id: 71 } } });
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
