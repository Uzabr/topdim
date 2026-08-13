import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { companyApi } from './api';
import CompanyRequestsTable from './CompanyRequestsTable';
import type { CompanyChangeRequest, CompanyChangeSummary, PageResponse } from './types';

vi.mock('./api', () => ({
  companyApi: {
    getPublishedProfile: vi.fn(),
    listRequests: vi.fn(),
    createDraft: vi.fn(),
    getRequest: vi.fn(),
    updateRequest: vi.fn(),
    deleteDraft: vi.fn(),
    submit: vi.fn(),
    withdraw: vi.fn(),
    copy: vi.fn(),
    uploadMedia: vi.fn(),
  },
}));

const mockedCompanyApi = vi.mocked(companyApi);

function summary(overrides: Partial<CompanyChangeSummary>): CompanyChangeSummary {
  return {
    id: 17,
    merchantId: 8,
    name: 'Market',
    status: 'PENDING_REVIEW',
    baseProfileVersion: 3,
    authorUserId: 41,
    authorStaffId: null,
    authorRole: 'OWNER',
    assigneeUserId: null,
    moderationComment: null,
    createdAt: '2026-08-13T09:00:00',
    updatedAt: '2026-08-13T12:00:00',
    submittedAt: '2026-08-13T10:00:00',
    assignedAt: null,
    ...overrides,
  };
}

function page(content: CompanyChangeSummary[], options: Partial<PageResponse<CompanyChangeSummary>> = {}) {
  return {
    content,
    number: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true,
    empty: content.length === 0,
    ...options,
  };
}

function requestFrom(row: CompanyChangeSummary, status = row.status): CompanyChangeRequest {
  return {
    ...row,
    status,
    description: null,
    logoUrl: null,
    coverUrl: null,
    email: null,
    website: null,
    contactPerson: null,
    locations: [],
    decidedAt: null,
    withdrawnAt: status === 'WITHDRAWN' ? '2026-08-13T13:00:00' : null,
    lockVersion: 1,
  };
}

function renderTable() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <MemoryRouter initialEntries={['/company']}>
          <Routes>
            <Route path="/company" element={<CompanyRequestsTable />} />
            <Route path="/company/requests/:id" element={<div>Редактор заявки открыт</div>} />
          </Routes>
        </MemoryRouter>
      </AntApp>
    </QueryClientProvider>,
  );
}

describe('CompanyRequestsTable', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('shows revision context and copies an outdated request into a new draft', async () => {
    const revision = summary({
      id: 17,
      status: 'REVISION_REQUESTED',
      moderationComment: 'Добавьте телефон основного филиала',
      assigneeUserId: 7,
    });
    const outdated = summary({
      id: 18,
      status: 'OUTDATED',
      submittedAt: null,
      moderationComment: 'Опубликована более новая версия профиля',
    });
    mockedCompanyApi.listRequests.mockResolvedValue(page([revision, outdated]));
    mockedCompanyApi.copy.mockResolvedValue(requestFrom(
      summary({ id: 99, status: 'DRAFT', submittedAt: null }),
    ));

    renderTable();

    expect(await screen.findByText('Добавьте телефон основного филиала')).toBeTruthy();
    expect(screen.getByText('Исполнитель #7')).toBeTruthy();
    expect(screen.getByText('Устарела')).toBeTruthy();
    expect(screen.getByText('Опубликована более новая версия профиля')).toBeTruthy();
    expect(screen.getByRole('button', { name: /Просмотреть/ })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Создать копию/ }));

    expect(await screen.findByText('Редактор заявки открыт')).toBeTruthy();
  });

  it('requests the next page from the server', async () => {
    mockedCompanyApi.listRequests.mockResolvedValue(page(
      [summary({ id: 17 })],
      { totalElements: 21, totalPages: 3, last: false },
    ));

    renderTable();

    expect(await screen.findByText('#17')).toBeTruthy();
    fireEvent.click(screen.getByTitle('2'));

    await waitFor(() => expect(mockedCompanyApi.listRequests).toHaveBeenLastCalledWith({
      page: 1,
      size: 10,
    }));
  });

  it('requires a nonblank withdrawal reason and refreshes the row after success', async () => {
    const user = userEvent.setup();
    const pending = summary({ id: 19, status: 'PENDING_REVIEW' });
    let withdrawn = false;
    mockedCompanyApi.listRequests.mockImplementation(async () => page([
      withdrawn ? { ...pending, status: 'WITHDRAWN' } : pending,
    ]));
    mockedCompanyApi.withdraw.mockImplementation(async (_id, reason) => {
      if (!reason.trim()) throw new Error('blank reason');
      withdrawn = true;
      return requestFrom(pending, 'WITHDRAWN');
    });

    renderTable();

    fireEvent.click(await screen.findByRole('button', { name: /Отозвать/ }));
    const confirm = screen.getByRole('button', { name: 'Подтвердить отзыв' }) as HTMLButtonElement;
    expect(confirm.disabled).toBe(true);

    await user.type(screen.getByLabelText('Причина отзыва'), '   ');
    expect(confirm.disabled).toBe(true);
    expect(mockedCompanyApi.withdraw).not.toHaveBeenCalled();

    await user.clear(screen.getByLabelText('Причина отзыва'));
    await user.type(screen.getByLabelText('Причина отзыва'), 'Компания временно закрыта');
    expect(confirm.disabled).toBe(false);
    await user.click(confirm);

    expect(await screen.findByText('Отозвана')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Отозвать/ })).toBeNull();
  });
});
