import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { companyApi } from './api';
import CompanyProfilePage from './CompanyProfilePage';
import type {
  CompanyChangeRequest,
  CompanyProfileChangeStatus,
  PageResponse,
  PublishedCompanyProfile,
} from './types';

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
const activeStatuses: CompanyProfileChangeStatus[] = [
  'DRAFT',
  'PENDING_REVIEW',
  'IN_REVIEW',
  'REVISION_REQUESTED',
];

const profile: PublishedCompanyProfile = {
  id: 8,
  name: 'Market',
  description: 'Семейный магазин рядом с домом',
  logoUrl: null,
  coverUrl: 'https://cdn.topdim.uz/merchant/market-cover.jpg',
  email: 'owner@market.uz',
  website: 'https://market.uz',
  contactPerson: 'Азиза',
  userId: 41,
  active: true,
  profileVersion: 3,
  publicationReady: false,
  publicationBlockReason: 'Не указан телефон',
  primaryLocation: {
    id: 71,
    title: 'Главный филиал',
    address: 'Ташкент, ул. Амира Темура, 1',
    phone: null,
    workingHours: '09:00–21:00',
    latitude: 41.311,
    longitude: 69.279,
    primary: true,
    active: true,
  },
  locations: [{
    id: 71,
    title: 'Главный филиал',
    address: 'Ташкент, ул. Амира Темура, 1',
    phone: null,
    workingHours: '09:00–21:00',
    latitude: 41.311,
    longitude: 69.279,
    primary: true,
    active: true,
  }],
};

const draft: CompanyChangeRequest = {
  id: 17,
  merchantId: 8,
  name: 'Market',
  status: 'DRAFT',
  baseProfileVersion: 3,
  authorUserId: 41,
  authorStaffId: null,
  authorRole: 'OWNER',
  assigneeUserId: null,
  moderationComment: null,
  createdAt: '2026-08-13T12:00:00',
  updatedAt: '2026-08-13T12:00:00',
  submittedAt: null,
  assignedAt: null,
  logoUrl: null,
  coverUrl: null,
  description: 'Семейный магазин рядом с домом',
  email: 'owner@market.uz',
  website: 'https://market.uz',
  contactPerson: 'Азиза',
  locations: [],
  decidedAt: null,
  withdrawnAt: null,
  lockVersion: 0,
};

function page(totalElements: number): PageResponse<never> {
  return {
    content: [],
    number: 0,
    size: 1,
    totalElements,
    totalPages: totalElements === 0 ? 0 : totalElements,
    first: true,
    last: totalElements <= 1,
    empty: totalElements === 0,
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <MemoryRouter initialEntries={['/company']}>
          <Routes>
            <Route path="/company" element={<CompanyProfilePage />} />
            <Route path="/company/requests/:id" element={<div>Редактор заявки открыт</div>} />
          </Routes>
        </MemoryRouter>
      </AntApp>
    </QueryClientProvider>,
  );
  return { ...rendered, queryClient };
}

describe('CompanyProfilePage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedCompanyApi.getPublishedProfile.mockResolvedValue(profile);
    mockedCompanyApi.listRequests.mockImplementation(async () => page(1));
    mockedCompanyApi.createDraft.mockResolvedValue(draft);
  });

  it('shows published profile readiness and opens the created draft', async () => {
    const { queryClient } = renderPage();
    const invalidateQueries = vi.spyOn(queryClient, 'invalidateQueries');

    expect(await screen.findByText('Опубликованный профиль')).toBeTruthy();
    expect(screen.getByText('Версия 3')).toBeTruthy();
    expect(screen.getByRole('img', { name: 'Обложка компании Market' })).toBeTruthy();
    expect(screen.getByLabelText('Логотип компании Market').textContent).toBe('M');
    expect(screen.getByText('Что нужно заполнить')).toBeTruthy();
    expect(screen.getByText('Не указан телефон')).toBeTruthy();
    expect(screen.getByText('09:00–21:00')).toBeTruthy();
    expect(await screen.findByText('4 из 10 активных')).toBeTruthy();
    activeStatuses.forEach((status) => {
      expect(mockedCompanyApi.listRequests).toHaveBeenCalledWith({ status, page: 0, size: 1 });
    });

    fireEvent.click(screen.getByRole('button', { name: /Создать заявку на изменение/ }));

    expect(await screen.findByText('Редактор заявки открыт')).toBeTruthy();
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['company-change-requests'] });
    expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['company-profile'] });
  });

  it('switches from the published profile to the paginated request history tab', async () => {
    renderPage();

    expect(await screen.findByText('Опубликованный профиль')).toBeTruthy();
    const requestsTab = screen.getByRole('tab', { name: 'Мои заявки' });
    expect(screen.queryByRole('heading', { name: 'Мои заявки' })).toBeNull();

    fireEvent.click(requestsTab);

    expect(await screen.findByRole('heading', { name: 'Мои заявки' })).toBeTruthy();
  });

  it('does not offer an eleventh active request', async () => {
    mockedCompanyApi.listRequests.mockImplementation(async (filters = {}) => {
      const { status } = filters;
      const counts: Record<CompanyProfileChangeStatus, number> = {
        DRAFT: 3,
        PENDING_REVIEW: 3,
        IN_REVIEW: 2,
        REVISION_REQUESTED: 2,
        APPROVED: 0,
        REJECTED: 0,
        WITHDRAWN: 0,
        OUTDATED: 0,
      };
      return page(status ? counts[status] : 0);
    });

    renderPage();

    expect(await screen.findByText('10 из 10 активных')).toBeTruthy();
    const createButton = screen.getByRole('button', {
      name: /Создать заявку на изменение/,
    }) as HTMLButtonElement;
    await waitFor(() => expect(createButton.disabled).toBe(true));
    expect(screen.getByText('Достигнут лимит активных заявок')).toBeTruthy();
  });

  it('fails closed when the active request limit cannot be checked', async () => {
    mockedCompanyApi.listRequests.mockImplementation(async (filters = {}) => {
      const { status } = filters;
      if (status === 'IN_REVIEW') {
        throw new Error('coupon service unavailable');
      }
      return page(1);
    });

    renderPage();

    expect(await screen.findByText('Не удалось проверить лимит активных заявок')).toBeTruthy();
    const createButton = screen.getByRole('button', {
      name: /Создать заявку на изменение/,
    }) as HTMLButtonElement;
    expect(createButton.disabled).toBe(true);
    fireEvent.click(createButton);
    expect(mockedCompanyApi.createDraft).not.toHaveBeenCalled();
  });
});
