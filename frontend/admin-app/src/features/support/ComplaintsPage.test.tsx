import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp } from 'antd';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getPendingComplaints, resolveComplaint, type AdminComplaint } from './api';
import { ComplaintsPage } from './ComplaintsPage';

vi.mock('./api', () => ({
  getPendingComplaints: vi.fn(),
  resolveComplaint: vi.fn(),
}));

const complaint: AdminComplaint = {
  id: 31,
  userId: 7,
  orderId: 101,
  purchasedCouponId: 201,
  couponTitle: 'Ужин на двоих',
  couponCode: 'CP-COMPLAINT',
  merchantName: 'Ali Cafe',
  subject: 'Купон не приняли',
  description: 'Кассир отказался принимать купон',
  status: 'PENDING',
  createdAt: '2026-08-04T10:00:00',
};

function page(content: AdminComplaint[]) {
  return {
    content,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    number: 0,
    size: 20,
  };
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <ComplaintsPage />
      </AntApp>
    </QueryClientProvider>,
  );
}

describe('ComplaintsPage', () => {
  beforeEach(() => {
    vi.mocked(getPendingComplaints).mockResolvedValue(page([complaint]));
    vi.mocked(resolveComplaint).mockResolvedValue();
  });

  it('shows a dedicated empty queue state', async () => {
    vi.mocked(getPendingComplaints).mockResolvedValue(page([]));

    renderPage();

    expect(await screen.findByText('Нет обращений на рассмотрении')).toBeTruthy();
  });

  it('shows a load error and retries the queue', async () => {
    vi.mocked(getPendingComplaints)
      .mockRejectedValueOnce(new Error('order-service unavailable'))
      .mockResolvedValueOnce(page([complaint]));
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Ошибка загрузки обращений')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));

    expect(await screen.findByText('CP-COMPLAINT')).toBeTruthy();
  });

  it('requires an answer and surfaces a concurrent-decision conflict', async () => {
    vi.mocked(resolveComplaint).mockRejectedValue({
      response: { data: { message: 'Обращение уже обработано другим сотрудником' } },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('CP-COMPLAINT');

    await user.click(screen.getByText('Отклонить'));
    await user.click(screen.getAllByRole('button', { name: 'Отклонить' }).at(-1)!);
    expect(resolveComplaint).not.toHaveBeenCalled();
    expect(await screen.findByText('Введите ответ')).toBeTruthy();

    await user.type(screen.getByPlaceholderText('Ответ / резолюция (обязательно)'), 'Купон уже использован');
    await user.click(screen.getAllByRole('button', { name: 'Отклонить' }).at(-1)!);

    expect(await screen.findByText('Обращение уже обработано другим сотрудником')).toBeTruthy();
  });
});
