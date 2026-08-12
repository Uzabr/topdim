import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../api';
import CouponRequestFormPage from './CouponRequestFormPage';

vi.mock('../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
  },
}));

const mockedApi = vi.mocked(api);

describe('CouponRequestFormPage edit mode', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedApi.put.mockResolvedValue({ data: { data: { id: 42 } } });
    mockedApi.get.mockImplementation(async (url: string) => {
      if (url === '/api/v1/categories') {
        return { data: { data: [{ id: 7, name: 'Красота', slug: 'beauty' }] } };
      }
      if (url === '/api/v1/partner/coupons/42') {
        return {
          data: {
            data: {
              id: 42,
              title: 'Маникюр VIP',
              category: { id: 7, name: 'Красота', slug: 'beauty' },
              offerDescription: 'Полный уход',
              oldPrice: 100_000,
              fromPrice: 60_000,
              discountPercent: 40,
              buyUntil: '2026-09-01T12:00:00',
              useUntil: '2026-10-01T12:00:00',
              giftAvailable: true,
              status: 'REVISION_REQUESTED',
              revisionComment: 'Уточните состав услуги',
              images: ['https://cdn.example.com/manicure.jpg'],
              options: [{
                id: 1,
                title: 'VIP',
                regularPrice: 100_000,
                couponPrice: 60_000,
                quantityLimit: 25,
                quantitySold: 0,
                status: 'ACTIVE',
              }],
            },
          },
        };
      }
      throw new Error(`Unexpected GET ${url}`);
    });
  });

  it('loads existing coupon fields and revision context', async () => {
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <MemoryRouter initialEntries={['/coupons/42/edit']}>
            <Routes>
              <Route path="/coupons/:id/edit" element={<CouponRequestFormPage />} />
            </Routes>
          </MemoryRouter>
        </AntApp>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('heading', { name: /Изменить предложение/i })).toBeTruthy();
    expect((screen.getByLabelText('Название предложения') as HTMLInputElement).value)
      .toBe('Маникюр VIP');
    expect(screen.getByDisplayValue('VIP')).toBeTruthy();
    expect(screen.getByText('Уточните состав услуги')).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: /Добавить вариант/i }));
    const optionTitles = screen.getAllByPlaceholderText('Базовый / VIP') as HTMLInputElement[];
    fireEvent.change(optionTitles[1], { target: { value: 'Стандарт' } });

    expect(optionTitles[0].value).toBe('VIP');
    expect(optionTitles[1].value).toBe('Стандарт');
    fireEvent.click(screen.getByRole('button', { name: /Сохранить изменения/i }));

    await waitFor(() => expect(mockedApi.put).toHaveBeenCalledWith(
      '/api/v1/partner/coupons/42',
      expect.objectContaining({
        title: 'Маникюр VIP',
        categoryId: 7,
        imageUrls: ['https://cdn.example.com/manicure.jpg'],
        options: [{
          title: 'VIP',
          regularPrice: 100_000,
          couponPrice: 60_000,
          quantityLimit: 25,
        }],
      }),
    ));
  });

  it('blocks direct editing when the coupon status is not editable', async () => {
    mockedApi.get.mockImplementation(async (url: string) => {
      if (url === '/api/v1/partner/coupons/42') {
        return {
          data: {
            data: {
              id: 42,
              title: 'Published offer',
              status: 'PUBLISHED',
            },
          },
        };
      }
      throw new Error(`Unexpected GET ${url}`);
    });
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <MemoryRouter initialEntries={['/coupons/42/edit']}>
            <Routes>
              <Route path="/coupons/:id/edit" element={<CouponRequestFormPage />} />
            </Routes>
          </MemoryRouter>
        </AntApp>
      </QueryClientProvider>,
    );

    expect(await screen.findByText('Редактирование недоступно')).toBeTruthy();
    expect(screen.queryByRole('button', { name: /Сохранить изменения/i })).toBeNull();
    expect(mockedApi.put).not.toHaveBeenCalled();
  });

  it('lets the browser add the multipart boundary when uploading a photo', async () => {
    mockedApi.post.mockResolvedValue({
      data: {
        data: {
          fileName: 'manicure.jpg',
          url: '/api/v1/media/manicure.jpg',
        },
      },
    });
    const queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    });

    const { container } = render(
      <QueryClientProvider client={queryClient}>
        <AntApp>
          <MemoryRouter initialEntries={['/coupons/new']}>
            <Routes>
              <Route path="/coupons/new" element={<CouponRequestFormPage />} />
            </Routes>
          </MemoryRouter>
        </AntApp>
      </QueryClientProvider>,
    );

    expect(await screen.findByRole('heading', { name: /Подать купонное предложение/i }))
      .toBeTruthy();
    const fileInput = container.querySelector('input[type="file"]');
    expect(fileInput).toBeTruthy();
    const file = new File(['photo'], 'manicure.jpg', { type: 'image/jpeg' });

    fireEvent.change(fileInput!, { target: { files: [file] } });

    await waitFor(() => expect(mockedApi.post).toHaveBeenCalledWith(
      '/api/v1/media/upload',
      expect.any(FormData),
    ));
    expect((await screen.findByAltText('Фото 1')).getAttribute('src'))
      .toBe('/api/v1/media/manicure.jpg');
  });
});
