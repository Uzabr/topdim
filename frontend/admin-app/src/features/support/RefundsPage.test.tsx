import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp } from 'antd';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  completeRefund, getAdminRefunds, rejectRefund, type AdminRefund,
} from './api';
import { RefundsPage } from './RefundsPage';

vi.mock('./api', () => ({
  approveRefund: vi.fn(),
  completeRefund: vi.fn(),
  getAdminRefunds: vi.fn(),
  rejectRefund: vi.fn(),
}));

const processingRefund: AdminRefund = {
  id: 11,
  orderId: 101,
  purchasedCouponId: 201,
  userId: 7,
  couponTitle: 'Ужин на двоих',
  couponCode: 'CP-REFUND1',
  merchantName: 'Ali Cafe',
  refundAmount: 0,
  reason: 'Не смог воспользоваться',
  status: 'APPROVED_PROCESSING',
  createdAt: '2026-08-04T10:00:00',
  expectedRefundAt: '2026-08-11T10:00:00',
};

function page(content: AdminRefund[]) {
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
        <RefundsPage />
      </AntApp>
    </QueryClientProvider>,
  );
}

describe('RefundsPage', () => {
  beforeEach(() => {
    vi.mocked(getAdminRefunds).mockResolvedValue(page([processingRefund]));
    vi.mocked(completeRefund).mockResolvedValue({ ...processingRefund, status: 'REFUNDED' });
    vi.mocked(rejectRefund).mockResolvedValue({ ...processingRefund, status: 'REJECTED' });
  });

  it('shows processing as money awaiting payout and preserves a zero amount', async () => {
    renderPage();

    expect(await screen.findByText('Ожидает выплаты')).toBeTruthy();
    expect(screen.getByText('0 сум')).toBeTruthy();
    expect(screen.getByText('11.08.2026')).toBeTruthy();
  });

  it('shows a dedicated empty state', async () => {
    vi.mocked(getAdminRefunds).mockResolvedValue(page([]));

    renderPage();

    expect(await screen.findByText('Нет возвратов')).toBeTruthy();
  });

  it('shows a load error and retries without pretending the list is empty', async () => {
    vi.mocked(getAdminRefunds)
      .mockRejectedValueOnce(new Error('order-service unavailable'))
      .mockResolvedValueOnce(page([processingRefund]));
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Ошибка загрузки возвратов')).toBeTruthy();
    expect(screen.queryByText('Нет возвратов')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));

    expect(await screen.findByText('CP-REFUND1')).toBeTruthy();
  });

  it('requires a rejection reason and surfaces a backend conflict', async () => {
    vi.mocked(getAdminRefunds).mockResolvedValue(page([{
      ...processingRefund,
      status: 'PENDING',
    }]));
    vi.mocked(rejectRefund).mockRejectedValue({
      response: { data: { message: 'Возврат уже обработан другим администратором' } },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('CP-REFUND1');

    await user.click(screen.getByText('Отклонить'));
    await user.click(screen.getByRole('button', { name: 'Отклонить возврат' }));
    expect(rejectRefund).not.toHaveBeenCalled();
    expect(await screen.findByText('Укажите причину отклонения')).toBeTruthy();

    await user.type(screen.getByPlaceholderText('Причина отклонения'), 'Услуга уже оказана');
    await user.click(screen.getByRole('button', { name: 'Отклонить возврат' }));

    expect(await screen.findByText('Возврат уже обработан другим администратором')).toBeTruthy();
  });

  it('warns that completion confirms an actual payout', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('CP-REFUND1');

    await user.click(screen.getByText('Завершить'));

    expect(await screen.findByText(
      'Подтверждайте завершение только после фактического возврата денег пользователю.',
    )).toBeTruthy();
  });
});
