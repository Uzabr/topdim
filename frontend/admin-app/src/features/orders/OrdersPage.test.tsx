import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { OrdersPage } from './OrdersPage';
import { fetchAdminOrders, type AdminOrder } from './api';

vi.mock('./api', () => ({
  fetchAdminOrders: vi.fn(),
}));

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <OrdersPage />
    </QueryClientProvider>,
  );
}

describe('OrdersPage', () => {
  beforeEach(() => {
    const order: AdminOrder & { orderNumber: string } = {
      id: 11,
      orderNumber: 'ORD-2026-0011',
      userId: 7,
      userEmail: 'user@example.com',
      userPhone: '+998901234567',
      status: 'COMPLETED',
      totalAmount: 150000,
      createdAt: '2026-08-04T10:30:00',
    };
    vi.mocked(fetchAdminOrders).mockResolvedValue({
      content: [order], totalElements: 1, totalPages: 1, number: 0, size: 20,
    });
  });

  it('shows the business order number and a localized terminal status', async () => {
    renderPage();

    expect(await screen.findByText('ORD-2026-0011')).toBeTruthy();
    expect(screen.getByText('Завершён')).toBeTruthy();
    expect(screen.queryByText('COMPLETED')).toBeNull();
  });

  it('shows the empty state when there are no orders', async () => {
    vi.mocked(fetchAdminOrders).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 20,
    });

    renderPage();

    expect(await screen.findByText('Нет заказов')).toBeTruthy();
  });

  it('shows a load error instead of an empty table', async () => {
    vi.mocked(fetchAdminOrders).mockRejectedValue(new Error('order-service unavailable'));

    renderPage();

    expect(await screen.findByText('Ошибка загрузки заказов')).toBeTruthy();
    expect(screen.queryByText('Нет заказов')).toBeNull();
  });

  it('renders a zero-value order as 0 sum instead of missing data', async () => {
    vi.mocked(fetchAdminOrders).mockResolvedValue({
      content: [{
        id: 12,
        orderNumber: 'ORD-2026-0012',
        userId: 8,
        userEmail: null,
        userPhone: null,
        status: 'PENDING',
        totalAmount: 0,
        createdAt: '2026-08-04T11:00:00',
      }],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    });

    renderPage();

    expect(await screen.findByText('0 сум')).toBeTruthy();
  });

  it('passes the refund-requested status selected by an administrator', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('ORD-2026-0011');

    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByText('Запрошен возврат'));

    expect(fetchAdminOrders).toHaveBeenLastCalledWith(0, 20, 'REFUND_REQUESTED');
  });
});
