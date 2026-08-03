import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../../api/client';
import { useAuthStore } from '../../../store/authStore';
import { CouponActionMenu } from './CouponActionMenu';
import type { AdminCouponRow, CouponStatus, StaffRole } from './types';

vi.mock('../../../api/client', () => ({
  default: {
    patch: vi.fn(),
    post: vi.fn(),
  },
}));

const mockedPatch = vi.mocked(api.patch);
const mockedPost = vi.mocked(api.post);

function coupon(
  status: CouponStatus,
  assignedModeratorId: number | null,
): AdminCouponRow {
  return {
    id: 42,
    title: 'Семейный купон PizzaLab',
    status,
    assignedModeratorId,
    assignedModeratorName: assignedModeratorId === 7 ? 'Модератор' : null,
    merchant: { id: 11, name: 'PizzaLab' },
    oldPrice: 120_000,
    fromPrice: 90_000,
    discountPercent: 25,
    buyUntil: null,
    useUntil: null,
    createdAt: '2026-08-03T12:00:00',
  };
}

function renderMenu(
  status: CouponStatus,
  role: StaffRole,
  assignedModeratorId: number | null,
) {
  useAuthStore.setState({
    accessToken: 'test-token',
    isAuthenticated: true,
    user: {
      id: 7,
      email: 'staff@sizbiz.uz',
      phone: '+998900000000',
      firstName: 'Тест',
      lastName: 'Сотрудник',
      role,
      avatarUrl: null,
    },
  });
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });

  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <CouponActionMenu coupon={coupon(status, assignedModeratorId)} />
      </MemoryRouter>
    </QueryClientProvider>,
  );

  return queryClient;
}

async function visibleActions(): Promise<string[]> {
  const user = userEvent.setup();
  await user.click(screen.getByRole('button', { name: 'Действия' }));
  return screen.getAllByRole('menuitem').map((item) => item.textContent ?? '');
}

describe('CouponActionMenu', () => {
  beforeEach(() => {
    mockedPatch.mockResolvedValue({ data: { success: true } });
    mockedPost.mockResolvedValue({ data: { success: true } });
  });

  afterEach(() => {
    useAuthStore.setState({ accessToken: null, user: null, isAuthenticated: false });
    vi.clearAllMocks();
  });

  it.each([
    ['MODERATOR', 'LEAD', null, ['Просмотреть', 'Взять в работу']],
    ['MODERATOR', 'DRAFT', 7, ['Просмотреть', 'Редактировать', 'Отправить на согласование']],
    ['MODERATOR', 'DRAFT', 99, ['Просмотреть']],
    ['MODERATOR', 'REVISION_REQUESTED', 7, ['Просмотреть', 'Редактировать', 'Отправить на согласование']],
    ['MODERATOR', 'REVISION_REQUESTED', 99, ['Просмотреть']],
    ['MODERATOR', 'WAITING_FOR_MERCHANT', 7, ['Просмотреть']],
    ['MODERATOR', 'ACTIVE', 7, ['Просмотреть']],
    ['MODERATOR', 'PAUSED', 7, ['Просмотреть']],
    ['MODERATOR', 'SOLD_OUT', 7, ['Просмотреть']],
    ['MODERATOR', 'ARCHIVED', 7, ['Просмотреть']],
    ['ADMIN', 'LEAD', null, ['Просмотреть', 'Взять в работу']],
    ['ADMIN', 'DRAFT', 99, ['Просмотреть', 'Редактировать', 'Отправить на согласование']],
    ['ADMIN', 'REVISION_REQUESTED', null, ['Просмотреть', 'Редактировать', 'Отправить на согласование']],
    ['ADMIN', 'WAITING_FOR_MERCHANT', 7, ['Просмотреть', 'Служебное решение']],
    ['ADMIN', 'ACTIVE', 7, ['Просмотреть', 'Приостановить']],
    ['ADMIN', 'PAUSED', 7, ['Просмотреть', 'Восстановить', 'Архивировать']],
    ['ADMIN', 'SOLD_OUT', 7, ['Просмотреть', 'Архивировать']],
    ['ADMIN', 'ARCHIVED', 7, ['Просмотреть']],
    ['SUPER_ADMIN', 'LEAD', null, ['Просмотреть', 'Взять в работу']],
    ['SUPER_ADMIN', 'DRAFT', 99, ['Просмотреть', 'Редактировать', 'Отправить на согласование']],
    ['SUPER_ADMIN', 'REVISION_REQUESTED', null, ['Просмотреть', 'Редактировать', 'Отправить на согласование']],
    ['SUPER_ADMIN', 'WAITING_FOR_MERCHANT', 7, ['Просмотреть', 'Служебное решение']],
    ['SUPER_ADMIN', 'ACTIVE', 7, ['Просмотреть', 'Приостановить']],
    ['SUPER_ADMIN', 'PAUSED', 7, ['Просмотреть', 'Восстановить', 'Архивировать']],
    ['SUPER_ADMIN', 'SOLD_OUT', 7, ['Просмотреть', 'Архивировать']],
    ['SUPER_ADMIN', 'ARCHIVED', 7, ['Просмотреть']],
  ] as const)('renders the exact centralized action set for %s %s owned by %s', async (
    role,
    status,
    assignedModeratorId,
    expected,
  ) => {
    renderMenu(status, role, assignedModeratorId);
    const actual = await visibleActions();

    expect(actual).toEqual(expected);
  });

  it.each([
    'LEAD',
    'DRAFT',
    'REVISION_REQUESTED',
    'ACTIVE',
    'PAUSED',
    'SOLD_OUT',
    'ARCHIVED',
  ] as const)('does not expose support review to an admin outside WAITING_FOR_MERCHANT (%s)', async (status) => {
    renderMenu(status, 'ADMIN', 7);

    expect(await visibleActions()).not.toContain('Служебное решение');
  });

  it.each([
    'LEAD',
    'DRAFT',
    'REVISION_REQUESTED',
    'WAITING_FOR_MERCHANT',
    'ACTIVE',
    'ARCHIVED',
  ] as const)('does not expose archive to an admin in an ineligible %s status', async (status) => {
    renderMenu(status, 'ADMIN', 7);

    expect(await visibleActions()).not.toContain('Архивировать');
  });

  it('keeps a moderator waiting coupon view-only and exposes no forbidden action', async () => {
    renderMenu('WAITING_FOR_MERCHANT', 'MODERATOR', 7);

    expect(await visibleActions()).toEqual(['Просмотреть']);
    expect(screen.queryByText('Приостановить')).toBeNull();
    expect(screen.queryByText('Восстановить')).toBeNull();
    expect(screen.queryByText('Архивировать')).toBeNull();
    expect(screen.queryByText(/Удалить|Отклонить заявку|Поддержка/)).toBeNull();
  });

  it('requires a non-empty reason for an admin support decision and sends the approved payload', async () => {
    const user = userEvent.setup();
    renderMenu('WAITING_FOR_MERCHANT', 'ADMIN', 7);

    await user.click(screen.getByRole('button', { name: 'Действия' }));
    await user.click(screen.getByRole('menuitem', { name: 'Служебное решение' }));

    expect(screen.getByText('Внимание: это служебное решение заменяет подтверждение партнёра.')).toBeTruthy();
    expect(screen.queryByRole('radio', { name: 'Отклонить купон' })).toBeNull();
    expect((screen.getByRole('button', { name: 'Подтвердить решение' }) as HTMLButtonElement).disabled)
      .toBe(true);

    await user.type(
      screen.getByRole('textbox', { name: 'Причина служебного решения' }),
      'Партнёр подтвердил по телефону, обращение SUP-42',
    );
    await user.click(screen.getByRole('button', { name: 'Подтвердить решение' }));

    await waitFor(() => expect(mockedPatch).toHaveBeenCalledWith(
      '/api/v1/mod/coupons/42/review',
      { status: 'APPROVE', reason: 'Партнёр подтвердил по телефону, обращение SUP-42' },
    ));
  });

  it('explains a 403 permission denial without retrying the mutation', async () => {
    const user = userEvent.setup();
    mockedPatch.mockRejectedValueOnce({ response: { status: 403 } });
    renderMenu('LEAD', 'MODERATOR', null);

    await user.click(screen.getByRole('button', { name: 'Действия' }));
    await user.click(screen.getByRole('menuitem', { name: 'Взять в работу' }));

    await waitFor(() => expect(screen.getByText('Недостаточно прав для выполнения действия')).toBeTruthy());
    expect(mockedPatch).toHaveBeenCalledOnce();
  });

  it('explains a 404 stale coupon response', async () => {
    const user = userEvent.setup();
    mockedPatch.mockRejectedValueOnce({ response: { status: 404 } });
    renderMenu('LEAD', 'MODERATOR', null);

    await user.click(screen.getByRole('button', { name: 'Действия' }));
    await user.click(screen.getByRole('menuitem', { name: 'Взять в работу' }));

    await waitFor(() => expect(screen.getByText('Купон удалён или ссылка устарела')).toBeTruthy());
  });

  it('shows a backend 409 conflict and refreshes the workspace ownership and status', async () => {
    const user = userEvent.setup();
    const queryClient = renderMenu('LEAD', 'MODERATOR', null);
    const invalidateSpy = vi.spyOn(queryClient, 'invalidateQueries');
    mockedPatch.mockRejectedValueOnce({
      response: { status: 409, data: { message: 'Купон уже взят другим модератором' } },
    });

    await user.click(screen.getByRole('button', { name: 'Действия' }));
    await user.click(screen.getByRole('menuitem', { name: 'Взять в работу' }));

    await waitFor(() => expect(screen.getByText('Купон уже взят другим модератором')).toBeTruthy());
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['admin-coupons', 'workspace'] });
  });
});
