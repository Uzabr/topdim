import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { PageResponse } from '../../../types';
import { CouponTableView } from './CouponTableView';
import type { AdminCouponRow } from './types';

const coupon: AdminCouponRow = {
  id: 42,
  title: 'Семейный купон PizzaLab',
  status: 'LEAD',
  assignedModeratorId: null,
  assignedModeratorName: null,
  merchant: { id: 11, name: 'PizzaLab' },
  fromPrice: 90_000,
  oldPrice: 120_000,
  discountPercent: 25,
  buyUntil: '2026-09-01T12:00:00',
  useUntil: '2026-10-01T12:00:00',
  createdAt: '2026-08-03T12:00:00',
};

function page(overrides: Partial<PageResponse<AdminCouponRow>> = {}): PageResponse<AdminCouponRow> {
  return {
    content: [coupon],
    pageable: { pageNumber: 1, pageSize: 20 },
    totalElements: 45,
    totalPages: 3,
    first: false,
    last: false,
    ...overrides,
  };
}

function renderTable(overrides: Partial<React.ComponentProps<typeof CouponTableView>> = {}) {
  const props: React.ComponentProps<typeof CouponTableView> = {
    activeTab: 'new',
    pageSize: 20,
    data: page(),
    isLoading: false,
    error: null,
    isStaleData: false,
    lastSuccessfulAt: null,
    onRetry: vi.fn(),
    onPageChange: vi.fn(),
    ...overrides,
  };

  render(<CouponTableView {...props} />);
  return props;
}

describe('CouponTableView', () => {
  it('uses backend total and current page metadata', () => {
    renderTable();

    expect(screen.getByText('Семейный купон PizzaLab')).toBeTruthy();
    expect(screen.getByText('Всего: 45')).toBeTruthy();
    expect(screen.getByTitle('2').className).toContain('ant-pagination-item-active');
  });

  it('converts a visible page number to the zero-based backend page', async () => {
    const user = userEvent.setup();
    const props = renderTable();

    await user.click(screen.getByTitle('3'));

    expect(props.onPageChange).toHaveBeenCalledWith(2, 20);
  });

  it('offers exactly 20, 50, and 100 rows and resets to the first page on size change', async () => {
    const user = userEvent.setup();
    const props = renderTable();
    const selector = screen.getByRole('combobox', { name: 'Размер страницы таблицы' });

    expect(screen.getAllByRole('option').map((option) => option.getAttribute('value')))
      .toEqual(['20', '50', '100']);
    await user.selectOptions(selector, '50');

    expect(props.onPageChange).toHaveBeenCalledWith(0, 50);
  });

  it('renders a general load error and retry instead of the empty state', async () => {
    const user = userEvent.setup();
    const onRetry = vi.fn();
    renderTable({
      data: undefined,
      error: new Error('Сервис временно недоступен'),
      onRetry,
    });

    expect(screen.getByText('Не удалось загрузить купоны')).toBeTruthy();
    expect(screen.queryByText('Новых купонов нет')).toBeNull();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it.each([
    [403, 'Недостаточно прав для просмотра купонов'],
    [404, 'Купон удалён или ссылка устарела'],
  ])('explains HTTP %s without showing an empty queue', (status, message) => {
    renderTable({
      data: undefined,
      error: { response: { status } },
    });

    expect(screen.getByText(message)).toBeTruthy();
    expect(screen.queryByText('Новых купонов нет')).toBeNull();
  });

  it('keeps retained rows visible and marks them as stale after a timeout', () => {
    renderTable({
      error: { code: 'ECONNABORTED', message: 'timeout' },
      isStaleData: true,
      lastSuccessfulAt: Date.UTC(2026, 7, 3, 10, 15),
    });

    expect(screen.getByText('Показаны последние сохранённые данные')).toBeTruthy();
    expect(screen.getByText(/Последнее успешное обновление:/)).toBeTruthy();
    expect(screen.getByText('Семейный купон PizzaLab')).toBeTruthy();
    expect(screen.queryByText('Новых купонов нет')).toBeNull();
  });

  it.each([
    ['new', 'Новых купонов нет'],
    ['waiting-partner', 'Нет купонов, ожидающих партнёра'],
    ['archived', 'Архив купонов пуст'],
  ] as const)('renders a tab-specific empty state for %s', (activeTab, emptyText) => {
    renderTable({
      activeTab,
      data: page({
        content: [],
        pageable: { pageNumber: 0, pageSize: 20 },
        totalElements: 0,
        totalPages: 0,
        first: true,
        last: true,
      }),
    });

    expect(screen.getByText(emptyText)).toBeTruthy();
    expect(screen.queryByText('Не удалось загрузить купоны')).toBeNull();
  });
});
