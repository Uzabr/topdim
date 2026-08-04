import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp } from 'antd';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getPendingReviews, reviewUserReview, type AdminReview } from './api';
import { ReviewsPage } from './ReviewsPage';

vi.mock('./api', () => ({
  getPendingReviews: vi.fn(),
  reviewUserReview: vi.fn(),
}));

const review: AdminReview = {
  id: 41,
  userId: 8,
  userName: 'Aziza',
  couponOfferId: 77,
  rating: 4,
  comment: 'Хорошее предложение',
  status: 'PENDING',
  createdAt: '2026-08-04T10:00:00',
};

function page(content: AdminReview[]) {
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
        <ReviewsPage />
      </AntApp>
    </QueryClientProvider>,
  );
}

describe('ReviewsPage', () => {
  beforeEach(() => {
    vi.mocked(getPendingReviews).mockResolvedValue(page([review]));
    vi.mocked(reviewUserReview).mockResolvedValue();
  });

  it('shows a load error with an explicit retry', async () => {
    vi.mocked(getPendingReviews)
      .mockRejectedValueOnce(new Error('coupon-service unavailable'))
      .mockResolvedValueOnce(page([review]));
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Ошибка загрузки отзывов')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));

    expect(await screen.findByText('Aziza')).toBeTruthy();
  });

  it('asks for confirmation before publishing a review', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Aziza');

    await user.click(screen.getByText('Одобрить'));

    expect(reviewUserReview).not.toHaveBeenCalled();
    expect((await screen.findAllByText('Опубликовать отзыв?')).length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: 'Опубликовать' }));
    expect(reviewUserReview).toHaveBeenCalledWith(41, 'APPROVE');
  });

  it('surfaces a backend conflict while rejecting a review', async () => {
    vi.mocked(reviewUserReview).mockRejectedValue({
      response: { data: { message: 'Отзыв уже обработан другим сотрудником' } },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Aziza');

    await user.click(screen.getByText('Отклонить'));
    await user.type(
      screen.getByPlaceholderText('Укажите причину отклонения (будет видна автору)'),
      'Нарушение правил публикации',
    );
    await user.click(screen.getByRole('button', { name: 'Отклонить' }));

    expect(await screen.findByText('Отзыв уже обработан другим сотрудником')).toBeTruthy();
  });
});
