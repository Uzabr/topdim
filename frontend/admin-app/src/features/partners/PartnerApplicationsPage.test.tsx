import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import type { PartnerApplication } from '../../types';
import { PartnerApplicationsPage } from './PartnerApplicationsPage';

vi.mock('../../api/client', () => ({
  default: {
    get: vi.fn(),
    patch: vi.fn(),
  },
}));

const application: PartnerApplication = {
  id: 17,
  firstName: 'Ali',
  lastName: 'Valiyev',
  phone: '+998901234567',
  email: 'ali@example.uz',
  companyName: 'Ali Cafe',
  city: 'Ташкент',
  address: 'ул. Амира Темура, 10',
  workingHours: '09:00–22:00',
  businessCategory: 'Кафе',
  website: null,
  telegramUsername: null,
  comment: null,
  source: 'WEB',
  status: 'PENDING',
  rejectionReason: null,
  reviewedBy: null,
  reviewedAt: null,
  linkedUserId: null,
  linkedMerchantId: null,
  createdAt: '2026-08-04T10:30:00',
  updatedAt: null,
};

function response(content: PartnerApplication[]) {
  return {
    data: {
      success: true,
      message: null,
      data: {
        content,
        pageable: { pageNumber: 0, pageSize: 20 },
        totalElements: content.length,
        totalPages: content.length ? 1 : 0,
        first: true,
        last: true,
      },
    },
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <MemoryRouter>
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <PartnerApplicationsPage />
        </AntApp>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('PartnerApplicationsPage', () => {
  beforeEach(() => {
    vi.mocked(api.get).mockResolvedValue(response([application]));
    vi.mocked(api.patch).mockResolvedValue({ data: { success: true } });
  });

  it('localizes statuses and sends the selected status filter', async () => {
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Ожидает решения')).toBeTruthy();
    expect(screen.queryByText('PENDING')).toBeNull();

    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByText('Отклонённые'));

    await waitFor(() => expect(api.get).toHaveBeenLastCalledWith(
      '/api/v1/admin/partner-applications',
      { params: { page: 0, size: 20, status: 'REJECTED' } },
    ));
  });

  it('shows a dedicated empty state', async () => {
    vi.mocked(api.get).mockResolvedValue(response([]));

    renderPage();

    expect(await screen.findByText('Нет заявок')).toBeTruthy();
  });

  it('shows a load error and can retry', async () => {
    vi.mocked(api.get)
      .mockRejectedValueOnce(new Error('identity-service unavailable'))
      .mockResolvedValueOnce(response([application]));
    const user = userEvent.setup();

    renderPage();

    expect(await screen.findByText('Ошибка загрузки заявок')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByText('Ali Cafe')).toBeTruthy();
  });

  it('surfaces the backend reason when rejection conflicts', async () => {
    vi.mocked(api.patch).mockRejectedValue({
      response: { data: { message: 'Заявка уже обработана другим администратором' } },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Ali Cafe');

    const openRejectButton = screen.getByText('Отклонить').closest('button');
    expect(openRejectButton).not.toBeNull();
    await user.click(openRejectButton as HTMLButtonElement);
    await user.type(screen.getByPlaceholderText('Укажите причину...'), 'Не хватает документов');
    const rejectButtons = screen.getAllByRole('button', { name: 'Отклонить' });
    await user.click(rejectButtons[rejectButtons.length - 1]);

    expect(await screen.findByText('Заявка уже обработана другим администратором')).toBeTruthy();
  });

  it('offers a safe approval retry for an application left in processing', async () => {
    vi.mocked(api.get).mockResolvedValue(response([{
      ...application,
      status: 'PROCESSING',
      linkedUserId: 42,
    }]));

    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Требует повтора')).toBeTruthy();
    await user.click(screen.getByText('Повторить одобрение'));
    expect(await screen.findByText('При повторе существующий пароль не меняется.')).toBeTruthy();
    const retryButtons = screen.getAllByRole('button', { name: 'Повторить одобрение' });
    await user.click(retryButtons[retryButtons.length - 1]);

    await waitFor(() => expect(api.patch).toHaveBeenCalledWith(
      '/api/v1/admin/partner-applications/17/approve',
      expect.not.objectContaining({ temporaryPassword: expect.any(String) }),
    ));
    expect(screen.queryByText('Отклонить')).toBeNull();
  });
});
