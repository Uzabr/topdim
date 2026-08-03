import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../../api/client';
import { CouponWorkspacePage } from './CouponWorkspacePage';

vi.mock('../../../api/client', () => ({
  default: {
    get: vi.fn(),
  },
}));

const mockedGet = vi.mocked(api.get);

function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname}{location.search}</output>;
}

function renderWorkspace(initialEntry = '/coupons') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <Routes>
          <Route
            path="/coupons"
            element={(
              <>
                <CouponWorkspacePage />
                <LocationProbe />
              </>
            )}
          />
          <Route path="/coupons/new" element={<LocationProbe />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

describe('CouponWorkspacePage', () => {
  beforeEach(() => {
    mockedGet.mockImplementation(async (url) => {
      if (url === '/api/v1/admin/merchants') {
        return {
          data: {
            data: [
              { id: 11, name: 'PizzaLab' },
              { id: 12, name: 'Spa House' },
            ],
          },
        };
      }

      if (url === '/api/v1/admin/coupons/assignees') {
        return {
          data: {
            data: [
              { id: 7, name: 'Алишер Модератор' },
            ],
          },
        };
      }

      throw new Error(`Unexpected URL: ${url}`);
    });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('renders the unified coupon shell and loads complete filter dictionaries', async () => {
    renderWorkspace();

    expect(screen.getByRole('heading', { name: 'Купоны' })).toBeTruthy();
    expect(screen.getAllByRole('tab')).toHaveLength(6);
    expect(screen.getByRole('tab', { name: 'Новые' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'В работе' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Требуют изменений' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Ожидают партнёра' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Опубликованные' })).toBeTruthy();
    expect(screen.getByRole('tab', { name: 'Архив' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Таблица' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Kanban' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Создать купон' })).toBeTruthy();

    await waitFor(() => {
      expect(mockedGet).toHaveBeenCalledWith('/api/v1/admin/merchants');
      expect(mockedGet).toHaveBeenCalledWith('/api/v1/admin/coupons/assignees');
    });
  });

  it('preserves filters and resets page when the active tab changes', async () => {
    const user = userEvent.setup();
    renderWorkspace(
      '/coupons?tab=new&view=table&search=pizza&merchantId=11&assignedModeratorId=7&page=4&size=50',
    );

    await user.click(screen.getByRole('tab', { name: 'Требуют изменений' }));

    expect(screen.getByTestId('location').textContent).toBe(
      '/coupons?tab=revision&view=table&search=pizza&merchantId=11&assignedModeratorId=7&page=0&size=50',
    );
  });

  it('writes independently loaded merchant and assignee filters to the URL', async () => {
    const user = userEvent.setup();
    renderWorkspace('/coupons?tab=in-progress&view=table&page=5&size=20');

    await screen.findByRole('option', { name: 'PizzaLab' });
    expect(screen.getByRole('option', { name: 'Алишер Модератор' })).toBeTruthy();

    await user.selectOptions(screen.getByRole('combobox', { name: 'Партнёр' }), '11');
    await user.selectOptions(screen.getByRole('combobox', { name: 'Ответственный' }), '7');
    await user.click(screen.getByRole('button', { name: 'Kanban' }));

    expect(screen.getByTestId('location').textContent).toBe(
      '/coupons?tab=in-progress&view=kanban&merchantId=11&assignedModeratorId=7&page=0&size=20',
    );
  });

  it('writes search to the URL only after exactly 300 ms', async () => {
    vi.useFakeTimers();
    renderWorkspace('/coupons?tab=new&view=table&page=3&size=20');

    fireEvent.change(screen.getByRole('searchbox', { name: 'Поиск купонов' }), {
      target: { value: '  spa  ' },
    });

    act(() => vi.advanceTimersByTime(299));
    expect(screen.getByTestId('location').textContent).not.toContain('search=');

    act(() => vi.advanceTimersByTime(1));
    expect(screen.getByTestId('location').textContent).toBe(
      '/coupons?tab=new&view=table&search=spa&page=0&size=20',
    );
  });

  it('normalizes an unsupported page size and can open the create route', async () => {
    const user = userEvent.setup();
    renderWorkspace('/coupons?tab=new&view=table&page=2&size=21');

    expect((screen.getByRole('combobox', {
      name: 'Размер страницы',
    }) as HTMLSelectElement).value).toBe('20');

    await user.click(screen.getByRole('button', { name: 'Создать купон' }));
    expect(screen.getByTestId('location').textContent).toBe('/coupons/new');
  });
});
