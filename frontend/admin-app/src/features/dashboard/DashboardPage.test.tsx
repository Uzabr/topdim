import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import { useAuthStore } from '../../store/authStore';
import type { UserRole } from '../../types';
import { DashboardPage } from './DashboardPage';

vi.mock('../../api/client', () => ({
  default: {
    get: vi.fn(),
  },
}));

const mockedGet = vi.mocked(api.get);

function setUser(role: UserRole, id = 7) {
  useAuthStore.getState().login('access-token', {
    id,
    email: `${role.toLowerCase()}@sizbiz.uz`,
    phone: '+998900000000',
    firstName: role === 'MODERATOR' ? 'Модератор' : 'Админ',
    lastName: 'Тестовый',
    role,
    avatarUrl: null,
  });
}

function renderDashboard() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <DashboardPage />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

function page(totalElements: number) {
  return {
    data: {
      data: {
        content: [],
        totalElements,
        totalPages: totalElements === 0 ? 0 : 1,
        pageable: { pageNumber: 0, pageSize: 1 },
        first: true,
        last: true,
      },
    },
  };
}

describe('DashboardPage', () => {
  beforeEach(() => {
    mockedGet.mockReset();
  });

  it('renders real admin metrics, seven-day sales, and recent orders', async () => {
    setUser('ADMIN');
    mockedGet.mockImplementation(async (url) => {
      if (url === '/api/v1/admin/dashboard') {
        return {
          data: {
            data: {
              ordersToday: 3,
              paidRevenueToday: 410000,
              pendingComplaints: 2,
              salesLast7Days: [
                { date: '2026-07-29', orders: 1, revenue: 100000 },
                { date: '2026-07-30', orders: 0, revenue: 0 },
                { date: '2026-07-31', orders: 0, revenue: 0 },
                { date: '2026-08-01', orders: 0, revenue: 0 },
                { date: '2026-08-02', orders: 2, revenue: 275000 },
                { date: '2026-08-03', orders: 0, revenue: 0 },
                { date: '2026-08-04', orders: 4, revenue: 410000 },
              ],
              recentOrders: [{
                id: 9,
                orderNumber: 'ORD-9',
                userEmail: 'buyer@sizbiz.uz',
                status: 'PAID',
                totalAmount: 90000,
                createdAt: '2026-08-04T09:00:00',
              }],
            },
          },
        };
      }
      if (url === '/api/v1/admin/coupons') return page(12);
      if (url === '/api/v1/admin/users') return page(34);
      throw new Error(`Unexpected URL: ${url}`);
    });

    renderDashboard();

    expect(await screen.findByLabelText('Заказы сегодня: 3')).toBeTruthy();
    expect(screen.getByLabelText('Активных купонов: 12')).toBeTruthy();
    expect(screen.getByLabelText('Пользователей: 34')).toBeTruthy();
    expect(screen.getByLabelText('Жалоб ожидают: 2')).toBeTruthy();
    expect(screen.getByRole('region', { name: 'Продажи за последние 7 дней' })).toBeTruthy();
    expect(screen.getByText('ORD-9')).toBeTruthy();
    expect(screen.queryByText('График будет здесь (Recharts)')).toBeNull();
    expect(screen.queryByText('Лента событий')).toBeNull();
  });

  it('renders a moderator queue without calling privileged admin endpoints', async () => {
    setUser('MODERATOR', 77);
    mockedGet.mockImplementation(async (url, config) => {
      if (url === '/api/v1/mod/complaints') return page(4);
      if (url !== '/api/v1/admin/coupons') throw new Error(`Unexpected URL: ${url}`);

      const params = config?.params as Record<string, string | number>;
      if (params.status === 'LEAD') return page(5);
      if (params.statuses === 'DRAFT,REVISION_REQUESTED') return page(2);
      if (params.status === 'WAITING_FOR_MERCHANT') return page(1);
      throw new Error(`Unexpected params: ${JSON.stringify(params)}`);
    });

    renderDashboard();

    expect(await screen.findByLabelText('Новых купонов: 5')).toBeTruthy();
    expect(screen.getByLabelText('Мои купоны в работе: 2')).toBeTruthy();
    expect(screen.getByLabelText('Ожидают партнёра: 1')).toBeTruthy();
    expect(screen.getByLabelText('Жалоб ожидают: 4')).toBeTruthy();

    await waitFor(() => {
      const urls = mockedGet.mock.calls.map(([url]) => url);
      expect(urls).not.toContain('/api/v1/admin/dashboard');
      expect(urls).not.toContain('/api/v1/admin/users');
    });
  });

  it('keeps independent metrics visible when the order dashboard is unavailable', async () => {
    setUser('SUPER_ADMIN');
    mockedGet.mockImplementation(async (url) => {
      if (url === '/api/v1/admin/dashboard') throw new Error('order-service unavailable');
      if (url === '/api/v1/admin/coupons') return page(8);
      if (url === '/api/v1/admin/users') return page(21);
      throw new Error(`Unexpected URL: ${url}`);
    });

    renderDashboard();

    expect((await screen.findByRole('alert')).textContent).toContain(
      'Данные заказов временно недоступны',
    );
    expect(screen.getByLabelText('Активных купонов: 8')).toBeTruthy();
    expect(screen.getByLabelText('Пользователей: 21')).toBeTruthy();
    expect(screen.getByLabelText('Заказы сегодня: недоступно')).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Повторить загрузку заказов' })).toBeTruthy();
  });
});
