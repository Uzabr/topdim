import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import { AuditLogPage } from './AuditLogPage';

vi.mock('../../api/client', () => ({ default: { get: vi.fn() } }));

const auditLog = {
  id: 31,
  userId: 7,
  action: 'CHANGE_ROLE',
  entityName: 'USER',
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

  it('renders the real backend contract without invented email or IP fields', async () => {
    renderPage();

    expect(await screen.findByText('Аудит сотрудников')).toBeTruthy();
    expect(await screen.findByText('ID: 7')).toBeTruthy();
    expect(screen.getByText('Пользователь #42')).toBeTruthy();
    expect(screen.getByText('Смена роли')).toBeTruthy();
    expect(screen.queryByText('IP Адрес')).toBeNull();
  });

  it('shows a dedicated empty state', async () => {
    vi.mocked(api.get).mockResolvedValue(response([]));
    renderPage();

    expect(await screen.findByText('Нет действий с сотрудниками')).toBeTruthy();
  });

  it('shows a load error and retries explicitly', async () => {
    vi.mocked(api.get)
      .mockRejectedValueOnce(new Error('identity-service unavailable'))
      .mockResolvedValueOnce(response());
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Ошибка загрузки аудита сотрудников')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByText('Роль изменена с MODERATOR на ADMIN')).toBeTruthy();
  });
});
