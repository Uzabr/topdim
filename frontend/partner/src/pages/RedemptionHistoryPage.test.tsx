import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import {
  MemoryRouter,
  Route,
  Routes,
  useLocation,
} from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../api';
import RedemptionHistoryPage from './RedemptionHistoryPage';

vi.mock('../api', () => ({
  default: { get: vi.fn() },
}));

const mockedApi = vi.mocked(api);

const emptyPageResponse = {
  data: {
    data: {
      content: [],
      number: 0,
      size: 20,
      totalElements: 0,
    },
  },
};

function LocationProbe() {
  const location = useLocation();
  return <span data-testid="location-search">{location.search}</span>;
}

function renderHistory(initialEntry: string) {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route
              path="/redemptions"
              element={(
                <>
                  <RedemptionHistoryPage />
                  <LocationProbe />
                </>
              )}
            />
          </Routes>
        </MemoryRouter>
      </AntApp>
    </QueryClientProvider>,
  );
}

describe('RedemptionHistoryPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads URL filters and renders the paginated history', async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        data: {
          content: [{
            id: 91,
            couponTitle: 'SPA',
            optionTitle: 'VIP',
            couponCode: 'CP-12',
            redeemedByStaff: 'Али',
            redeemMethod: 'QR',
            redeemedAt: '2026-08-06T12:30:00',
          }],
          number: 1,
          size: 20,
          totalElements: 21,
        },
      },
    });

    renderHistory('/redemptions?code=CP-12&from=2026-08-01&to=2026-08-06&page=2');

    expect(await screen.findByText('SPA')).toBeTruthy();
    expect(screen.getByText('VIP')).toBeTruthy();
    expect(screen.getByText('Али')).toBeTruthy();
    expect(screen.getByText('QR')).toBeTruthy();
    expect(mockedApi.get).toHaveBeenCalledWith('/api/v1/partner/redemptions', {
      params: {
        page: 1,
        size: 20,
        couponCode: 'CP-12',
        dateFrom: '2026-08-01',
        dateTo: '2026-08-06',
      },
    });
  });

  it('applies code search and resets the page to one', async () => {
    mockedApi.get.mockResolvedValue(emptyPageResponse);
    renderHistory('/redemptions?page=4');

    fireEvent.change(await screen.findByPlaceholderText('CP-XXXX1234'), {
      target: { value: ' cp-vip ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Найти' }));

    await waitFor(() => expect(screen.getByTestId('location-search').textContent)
      .toBe('?code=cp-vip'));
    await waitFor(() => expect(mockedApi.get).toHaveBeenLastCalledWith(
      '/api/v1/partner/redemptions',
      { params: { page: 0, size: 20, couponCode: 'cp-vip' } },
    ));
  });

  it('moves server pagination through the canonical URL', async () => {
    mockedApi.get.mockResolvedValue({
      data: {
        data: {
          content: [{
            id: 91,
            couponTitle: 'SPA',
            couponCode: 'CP-12',
            redeemedAt: '2026-08-06T12:30:00',
          }],
          number: 0,
          size: 20,
          totalElements: 41,
        },
      },
    });
    renderHistory('/redemptions?code=CP-12');

    expect(await screen.findByText('SPA')).toBeTruthy();
    fireEvent.click(screen.getByTitle('2'));

    await waitFor(() => expect(screen.getByTestId('location-search').textContent)
      .toBe('?code=CP-12&page=2'));
    await waitFor(() => expect(mockedApi.get).toHaveBeenLastCalledWith(
      '/api/v1/partner/redemptions',
      { params: { page: 1, size: 20, couponCode: 'CP-12' } },
    ));
  });

  it('distinguishes empty history from an empty filtered result', async () => {
    mockedApi.get.mockResolvedValue(emptyPageResponse);
    const first = renderHistory('/redemptions');
    expect(await screen.findByText('Погашений пока нет')).toBeTruthy();
    first.unmount();

    renderHistory('/redemptions?code=missing');
    expect(await screen.findByText('По заданным фильтрам ничего не найдено')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Сбросить' }));
    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toBe(''));
  });

  it('shows an error and retries the same query', async () => {
    mockedApi.get
      .mockRejectedValueOnce(new Error('network error'))
      .mockResolvedValueOnce(emptyPageResponse);
    renderHistory('/redemptions?code=CP-1');

    expect(await screen.findByText('Не удалось загрузить историю погашений')).toBeTruthy();
    fireEvent.click(screen.getByRole('button', { name: 'Повторить' }));
    await waitFor(() => expect(mockedApi.get).toHaveBeenCalledTimes(2));
  });

  it('replaces malformed URL parameters with their normalized form', async () => {
    mockedApi.get.mockResolvedValue(emptyPageResponse);
    renderHistory('/redemptions?from=2026-02-31&page=-2');

    await waitFor(() => expect(screen.getByTestId('location-search').textContent).toBe(''));
    expect(mockedApi.get).toHaveBeenCalledWith('/api/v1/partner/redemptions', {
      params: { page: 0, size: 20 },
    });
  });
});
