import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import { AuditLogPage } from './AuditLogPage';

vi.mock('../../api/client', () => ({ default: { get: vi.fn() } }));

const auditLog = {
  id: 31,
  userId: 7,
  userEmail: 'admin@topdim.uz',
  userName: 'Admin Tester',
  userRole: 'ADMIN',
  action: 'CHANGE_ROLE',
  entityName: 'staff',
  entityId: 42,
  details: 'Роль изменена с MODERATOR на ADMIN',
  createdAt: '2026-08-04T10:30:00',
};

function response(content = [auditLog]) {
  return {
    data: {
      success: true,
      data: {
        content,
        totalElements: content.length,
        totalPages: content.length ? 1 : 0,
        number: 0,
        size: 20,
      },
    },
  };
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <AuditLogPage />
    </QueryClientProvider>,
  );
}

describe('AuditLogPage', () => {
  beforeEach(() => vi.mocked(api.get).mockResolvedValue(response()));

  it('renders actor, role, section, action and timestamp', async () => {
    renderPage();

    expect(await screen.findByText('Журнал действий')).toBeTruthy();
    expect(await screen.findByText('Admin Tester')).toBeTruthy();
    expect(screen.getByText('admin@topdim.uz')).toBeTruthy();
    expect(screen.getByText('Админ')).toBeTruthy();
    expect(screen.getByText('Сотрудники #42')).toBeTruthy();
    expect(screen.getByText('Смена роли')).toBeTruthy();
  });

  it('requests admin audit endpoint with search filter', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Admin Tester');

    await user.type(screen.getByPlaceholderText('Поиск по имени, email, деталям...'), 'admin');

    await waitFor(() => {
      expect(api.get).toHaveBeenCalledWith(
        '/api/v1/admin/audit-logs',
        expect.objectContaining({
          params: expect.objectContaining({
            search: 'admin',
            sort: 'createdAt,desc',
          }),
        }),
      );
    });
  });

  it('shows a dedicated empty state', async () => {
    vi.mocked(api.get).mockResolvedValue(response([]));
    renderPage();

    expect(await screen.findByText('Нет записей в журнале')).toBeTruthy();
  });

  it('shows a load error and retries explicitly', async () => {
    vi.mocked(api.get)
      .mockRejectedValueOnce(new Error('identity-service unavailable'))
      .mockResolvedValueOnce(response());
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Ошибка загрузки журнала действий')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByText('Роль изменена с MODERATOR на ADMIN')).toBeTruthy();
  });
});
