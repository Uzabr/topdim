import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../api';
import CouponsPage from './CouponsPage';

vi.mock('../api', () => ({
  default: { get: vi.fn() },
}));

const mockedApi = vi.mocked(api);

describe('CouponsPage edit navigation', () => {
  beforeEach(() => {
    mockedApi.get.mockResolvedValue({
      data: {
        data: {
          content: [{
            id: 42,
            title: 'Editable offer',
            fromPrice: 50_000,
            status: 'LEAD',
            totalSold: 0,
          }],
          totalElements: 1,
        },
      },
    });
  });

  it('opens the edit route for an editable coupon', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <MemoryRouter initialEntries={['/coupons']}>
            <Routes>
              <Route path="/coupons" element={<CouponsPage />} />
              <Route path="/coupons/:id/edit" element={<div>Edit route opened</div>} />
            </Routes>
          </MemoryRouter>
        </AntApp>
      </QueryClientProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: /Изменить/i }));

    expect(await screen.findByText('Edit route opened')).toBeTruthy();
  });
});
