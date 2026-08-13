import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { BrowserRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import * as profileChangesApi from './api';
import { MerchantProfileChangeQueuePage } from './MerchantProfileChangeQueuePage';
import type { MerchantProfileChangePage } from './types';

vi.mock('./api', () => ({
  MERCHANT_PROFILE_CHANGES_QUERY_KEY: ['merchant-profile-changes'],
  fetchMerchantProfileChanges: vi.fn(),
  fetchPendingMerchantProfileChangeCount: vi.fn(),
  takeMerchantProfileChangeToWork: vi.fn(),
}));

const pendingRequest = {
  id: 71,
  merchantId: 9,
  name: 'Safia Cafe',
  status: 'PENDING_REVIEW' as const,
  baseProfileVersion: 3,
  authorUserId: 18,
  authorStaffId: 24,
  authorRole: 'MANAGER',
  assigneeUserId: null,
  moderationComment: null,
  createdAt: '2026-08-10T09:00:00',
  updatedAt: '2026-08-11T10:00:00',
  submittedAt: '2026-08-11T10:00:00',
  assignedAt: null,
};

function page(content = [pendingRequest]): MerchantProfileChangePage {
  return {
    content,
    pageable: { pageNumber: 0, pageSize: 20 },
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true,
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <BrowserRouter>
      <QueryClientProvider client={queryClient}>
        <MerchantProfileChangeQueuePage />
      </QueryClientProvider>
    </BrowserRouter>,
  );
}

describe('MerchantProfileChangeQueuePage', () => {
  beforeEach(() => {
    vi.mocked(profileChangesApi.fetchMerchantProfileChanges).mockResolvedValue(page());
    vi.mocked(profileChangesApi.fetchPendingMerchantProfileChangeCount).mockResolvedValue(1);
    vi.mocked(profileChangesApi.takeMerchantProfileChangeToWork).mockResolvedValue();
  });

  it('shows submitted company changes and claims an unassigned request', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Изменения компаний' })).toBeTruthy();
    expect(await screen.findByText('Safia Cafe')).toBeTruthy();

    await user.click(screen.getByRole('button', { name: 'Взять в работу' }));

    await waitFor(() => {
      expect(profileChangesApi.takeMerchantProfileChangeToWork).toHaveBeenCalledWith(71);
    });
    expect(await screen.findByText('Заявка взята в работу')).toBeTruthy();
  });

  it('does not offer an unfiltered queue that could expose partner drafts', async () => {
    renderPage();

    await screen.findByText('Safia Cafe');
    expect(screen.queryByRole('tab', { name: 'Все' })).toBeNull();
    expect(profileChangesApi.fetchMerchantProfileChanges).toHaveBeenCalledWith(
      expect.objectContaining({ status: 'PENDING_REVIEW' }),
    );
  });

  it('changes status, search, assignee, dates and server page size', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Safia Cafe');

    await user.click(screen.getByRole('tab', { name: 'В работе' }));
    await user.type(screen.getByPlaceholderText('Компания, автор или ID'), 'safia');
    await user.type(screen.getByLabelText('ID исполнителя'), '42');

    const dateInputs = document.querySelectorAll<HTMLInputElement>('.ant-picker-input input');
    await user.click(dateInputs[0]);
    const startDate = document.querySelector<HTMLElement>('[title="2026-08-01"]');
    expect(startDate).toBeTruthy();
    await user.click(startDate!);
    const endDate = document.querySelector<HTMLElement>('[title="2026-08-13"]');
    expect(endDate).toBeTruthy();
    await user.click(endDate!);

    await waitFor(() => {
      expect(profileChangesApi.fetchMerchantProfileChanges).toHaveBeenCalledWith(
        expect.objectContaining({
          status: 'IN_REVIEW',
          search: 'safia',
          assigneeUserId: 42,
          submittedFrom: '2026-08-01T00:00:00',
          submittedTo: '2026-08-13T23:59:59',
          page: 0,
          size: 20,
        }),
      );
    });
  });

  it('refreshes the queue after a concurrent claim conflict', async () => {
    vi.mocked(profileChangesApi.takeMerchantProfileChangeToWork).mockRejectedValue({
      response: { data: { message: 'Заявка уже взята в работу' } },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Safia Cafe');

    await user.click(screen.getByRole('button', { name: 'Взять в работу' }));

    expect(await screen.findByText('Заявка уже взята в работу')).toBeTruthy();
    await waitFor(() => {
      expect(profileChangesApi.fetchMerchantProfileChanges).toHaveBeenCalledTimes(2);
    });
  });
});
