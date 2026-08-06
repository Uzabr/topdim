import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { MerchantDetailPage } from './MerchantDetailPage';
import {
  fetchMerchantCoupons,
  fetchMerchantDetail,
  setMerchantActive,
  updateMerchant,
} from './api';

vi.mock('./api', () => ({
  fetchMerchantCoupons: vi.fn(),
  fetchMerchantDetail: vi.fn(),
  setMerchantActive: vi.fn(),
  updateMerchant: vi.fn(),
}));

const merchant = {
  id: 7,
  name: 'SPA Oasis',
  description: 'Описание',
  logoUrl: '/logo.jpg',
  coverUrl: '/cover.jpg',
  email: 'spa@example.com',
  website: 'https://spa.example.com',
  contactPerson: 'Алишер',
  userId: 17,
  active: true,
  publicationReady: true,
  publicationBlockReason: null,
  primaryLocation: null,
  locations: [
    {
      id: 71,
      title: 'Главный филиал',
      address: 'Ташкент, ул. Амира Темура, 1',
      phone: '+998901234567',
      workingHours: '09:00-21:00',
      latitude: 41.3111,
      longitude: 69.2797,
      primary: true,
      active: true,
    },
  ],
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <MemoryRouter initialEntries={['/catalog/merchants/7']}>
          <Routes>
            <Route path="/catalog/merchants/:id" element={<MerchantDetailPage />} />
          </Routes>
        </MemoryRouter>
      </AntApp>
    </QueryClientProvider>,
  );
}

describe('MerchantDetailPage', () => {
  beforeEach(() => {
    vi.mocked(fetchMerchantDetail).mockResolvedValue(merchant);
    vi.mocked(fetchMerchantCoupons).mockResolvedValue({
      content: [], totalElements: 0, totalPages: 0, number: 0, size: 10,
    });
    vi.mocked(updateMerchant).mockResolvedValue(merchant);
    vi.mocked(setMerchantActive).mockResolvedValue(merchant);
  });

  it('preserves location identity and coordinates when saving merchant details', async () => {
    const user = userEvent.setup();
    renderPage();

    await user.click(await screen.findByRole('button', { name: /Редактировать/ }));
    await user.click(screen.getByRole('button', { name: 'Сохранить' }));

    await waitFor(() => {
      expect(updateMerchant).toHaveBeenCalledWith(7, expect.objectContaining({
        locations: [expect.objectContaining({
          id: 71,
          latitude: 41.3111,
          longitude: 69.2797,
        })],
      }));
    });
  });
});
