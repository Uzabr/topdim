import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../../api/client';
import { CouponKanbanView } from './CouponKanbanView';
import type { AdminCouponRow, CouponStatus, CouponWorkspaceState } from './types';

vi.mock('../../../api/client', () => ({
  default: {
    get: vi.fn(),
  },
}));

const mockedGet = vi.mocked(api.get);

const state: CouponWorkspaceState = {
  tab: 'new',
  view: 'kanban',
  search: 'pizza',
  merchantId: 11,
  assignedModeratorId: null,
  page: 0,
  pageSize: 100,
};

function coupon(id: number, title: string, status: CouponStatus): AdminCouponRow {
  return {
    id,
    title,
    status,
    assignedModeratorId: null,
    assignedModeratorName: null,
    merchant: { id: 11, name: 'PizzaLab' },
    oldPrice: 120_000,
    fromPrice: 90_000,
    discountPercent: 25,
    buyUntil: null,
    useUntil: null,
    createdAt: '2026-08-03T12:00:00',
  };
}

function page(
  content: AdminCouponRow[],
  pageNumber = 0,
  last = true,
) {
  return {
    content,
    pageable: { pageNumber, pageSize: 20 },
    totalElements: last ? content.length : 21,
    totalPages: last ? 1 : 2,
    first: pageNumber === 0,
    last,
  };
}

function renderKanban() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <CouponKanbanView state={state} />
    </QueryClientProvider>,
  );
}

describe('CouponKanbanView', () => {
  beforeEach(() => {
    mockedGet.mockImplementation(async (_url, config) => {
      const status = config?.params?.status as CouponStatus;
      const pageNumber = Number(config?.params?.page ?? 0);
      if (status === 'LEAD' && pageNumber === 0) {
        return { data: { data: page([coupon(1, 'Новый PizzaLab', status)], 0, false) } };
      }
      if (status === 'LEAD' && pageNumber === 1) {
        return { data: { data: page([coupon(2, 'Ещё один новый купон', status)], 1, true) } };
      }

      return {
        data: {
          data: page([coupon(
            status === 'DRAFT' ? 3 : status === 'REVISION_REQUESTED' ? 4 : 5,
            `Карточка ${status}`,
            status,
          )]),
        },
      };
    });
  });

  it('loads four operational columns with independent size-20 requests', async () => {
    renderKanban();

    expect(await screen.findByText('Новый PizzaLab')).toBeTruthy();
    expect(screen.getByText('Карточка DRAFT')).toBeTruthy();
    expect(screen.getByText('Карточка REVISION_REQUESTED')).toBeTruthy();
    expect(screen.getByText('Карточка WAITING_FOR_MERCHANT')).toBeTruthy();

    const requestParams = mockedGet.mock.calls.map(([, config]) => config?.params);
    expect(requestParams).toHaveLength(4);
    expect(requestParams.every((params) => params?.size === 20)).toBe(true);
    expect(requestParams.some((params) => params?.size === 500)).toBe(false);
    expect(requestParams.map((params) => params?.status).sort()).toEqual([
      'DRAFT',
      'LEAD',
      'REVISION_REQUESTED',
      'WAITING_FOR_MERCHANT',
    ]);
  });

  it('loads the next page only for the requested column', async () => {
    const user = userEvent.setup();
    renderKanban();

    await screen.findByText('Новый PizzaLab');
    await user.click(screen.getByRole('button', { name: 'Показать ещё: Новые' }));

    expect(await screen.findByText('Ещё один новый купон')).toBeTruthy();
    expect(mockedGet).toHaveBeenCalledWith('/api/v1/admin/coupons', {
      params: expect.objectContaining({
        status: 'LEAD',
        search: 'pizza',
        merchantId: 11,
        page: 1,
        size: 20,
      }),
    });
    expect(mockedGet.mock.calls.filter(([, config]) => config?.params?.status === 'DRAFT'))
      .toHaveLength(1);
  });

  it('keeps successful columns visible when one column fails and retries only that column', async () => {
    const user = userEvent.setup();
    let draftAttempts = 0;
    mockedGet.mockImplementation(async (_url, config) => {
      const status = config?.params?.status as CouponStatus;
      if (status === 'DRAFT') {
        draftAttempts += 1;
        if (draftAttempts === 1) {
          throw new Error('draft unavailable');
        }
      }
      return { data: { data: page([coupon(10, `Успех ${status}`, status)]) } };
    });

    renderKanban();

    expect(await screen.findByText('Не удалось загрузить колонку «В работе»')).toBeTruthy();
    expect(screen.getByText('Успех LEAD')).toBeTruthy();
    expect(screen.getByText('Успех REVISION_REQUESTED')).toBeTruthy();
    expect(screen.getByText('Успех WAITING_FOR_MERCHANT')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Повторить: В работе' }));

    await waitFor(() => expect(screen.getByText('Успех DRAFT')).toBeTruthy());
    expect(draftAttempts).toBe(2);
    expect(screen.getByText('Успех LEAD')).toBeTruthy();
  });
});
