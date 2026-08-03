import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../../api/client';
import { useAuthStore } from '../../../store/authStore';
import { CouponActionMenu } from './CouponActionMenu';
import { allowedCouponActions } from './permissions';
import type { AdminCouponRow, CouponAction, CouponStatus, StaffRole } from './types';

vi.mock('../../../api/client', () => ({
  default: {
    patch: vi.fn(),
    post: vi.fn(),
  },
}));

const mockedPatch = vi.mocked(api.patch);
const mockedPost = vi.mocked(api.post);

const labels: Record<CouponAction, string> = {
  view: 'Просмотреть',
  'take-to-work': 'Взять в работу',
  edit: 'Редактировать',
  'send-to-approval': 'Отправить на согласование',
  'support-review': 'Служебное решение',
  pause: 'Приостановить',
  restore: 'Восстановить',
  archive: 'Архивировать',
};

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
    defaultOptions: { mutations: { retry: false }, queries: { retry: false } },
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
    ['MODERATOR', 'LEAD', null],
    ['MODERATOR', 'DRAFT', 7],
    ['MODERATOR', 'DRAFT', 99],
    ['MODERATOR', 'REVISION_REQUESTED', 7],
    ['MODERATOR', 'REVISION_REQUESTED', 99],
    ['MODERATOR', 'WAITING_FOR_MERCHANT', 7],
    ['MODERATOR', 'ACTIVE', 7],
    ['MODERATOR', 'PAUSED', 7],
    ['MODERATOR', 'SOLD_OUT', 7],
    ['MODERATOR', 'ARCHIVED', 7],
    ['ADMIN', 'LEAD', null],
    ['ADMIN', 'DRAFT', 99],
    ['ADMIN', 'REVISION_REQUESTED', null],
    ['ADMIN', 'WAITING_FOR_MERCHANT', 7],
    ['ADMIN', 'ACTIVE', 7],
    ['ADMIN', 'PAUSED', 7],
    ['ADMIN', 'SOLD_OUT', 7],
    ['ADMIN', 'ARCHIVED', 7],
    ['SUPER_ADMIN', 'LEAD', null],
    ['SUPER_ADMIN', 'DRAFT', 99],
    ['SUPER_ADMIN', 'REVISION_REQUESTED', null],
    ['SUPER_ADMIN', 'WAITING_FOR_MERCHANT', 7],
    ['SUPER_ADMIN', 'ACTIVE', 7],
    ['SUPER_ADMIN', 'PAUSED', 7],
    ['SUPER_ADMIN', 'SOLD_OUT', 7],
    ['SUPER_ADMIN', 'ARCHIVED', 7],
  ] as const)('renders the exact centralized action set for %s %s owned by %s', async (
    role,
    status,
    assignedModeratorId,
  ) => {
    renderMenu(status, role, assignedModeratorId);
    const actual = await visibleActions();
    const expected = allowedCouponActions(coupon(status, assignedModeratorId), { id: 7, role })
      .map((action) => labels[action]);

    expect(actual).toEqual(expected);
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
