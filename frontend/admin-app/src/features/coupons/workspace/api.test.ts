import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../../api/client';
import { ADMIN_COUPONS_WORKSPACE_QUERY_KEY, fetchAdminCoupons } from './api';
import type { CouponWorkspaceState } from './types';

vi.mock('../../../api/client', () => ({
  default: {
    get: vi.fn(),
  },
}));

const mockedGet = vi.mocked(api.get);

const pageResponse = {
  content: [],
  pageable: { pageNumber: 0, pageSize: 20 },
  totalElements: 0,
  totalPages: 0,
  first: true,
  last: true,
};

const baseState: CouponWorkspaceState = {
  tab: 'new',
  view: 'table',
  search: '',
  merchantId: null,
  assignedModeratorId: null,
  page: 0,
  pageSize: 20,
};

describe('coupon workspace API', () => {
  beforeEach(() => {
    mockedGet.mockResolvedValue({ data: { data: pageResponse } });
  });

  it('uses the stable workspace query-key prefix', () => {
    expect(ADMIN_COUPONS_WORKSPACE_QUERY_KEY).toEqual([
      'admin-coupons',
      'workspace',
    ]);
  });

  it('sends one backend status for a single-status product tab', async () => {
    await fetchAdminCoupons(baseState);

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/admin/coupons', {
      params: {
        status: 'LEAD',
        page: 0,
        size: 20,
      },
    });
  });

  it('sends a comma-separated status group for published coupons', async () => {
    await fetchAdminCoupons({
      ...baseState,
      tab: 'published',
      page: 4,
      pageSize: 100,
    });

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/admin/coupons', {
      params: {
        statuses: 'ACTIVE,PAUSED,SOLD_OUT',
        page: 4,
        size: 100,
      },
    });
  });

  it('trims search, sends numeric filters, and can override the requested page', async () => {
    await fetchAdminCoupons({
      ...baseState,
      tab: 'revision',
      search: '  pizza  ',
      merchantId: 12,
      assignedModeratorId: 7,
      page: 8,
      pageSize: 50,
    }, 2);

    expect(mockedGet).toHaveBeenCalledWith('/api/v1/admin/coupons', {
      params: {
        status: 'REVISION_REQUESTED',
        search: 'pizza',
        merchantId: 12,
        assignedModeratorId: 7,
        page: 2,
        size: 50,
      },
    });
  });

  it('omits blank search and nullable filters', async () => {
    await fetchAdminCoupons({
      ...baseState,
      tab: 'archived',
      search: '   ',
    });

    const [, config] = mockedGet.mock.calls[0];
    expect(config?.params).toEqual({
      status: 'ARCHIVED',
      page: 0,
      size: 20,
    });
    expect(config?.params).not.toHaveProperty('search');
    expect(config?.params).not.toHaveProperty('merchantId');
    expect(config?.params).not.toHaveProperty('assignedModeratorId');
  });
});
