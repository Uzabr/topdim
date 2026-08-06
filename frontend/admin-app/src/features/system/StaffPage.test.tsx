import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import { StaffPage } from './StaffPage';

vi.mock('../../api/client', () => ({
  default: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}));

const staff = {
  id: 7,
  firstName: 'Ali',
  lastName: 'Valiyev',
  email: 'admin@topdim.uz',
  phone: '+998901234567',
  role: 'ADMIN',
  enabled: true,
  createdAt: '2026-08-04T10:30:00',
};

function response(content = [staff]) {
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
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AntApp><StaffPage /></AntApp>
    </QueryClientProvider>,
  );
}

describe('StaffPage', () => {
  beforeEach(() => {
    vi.mocked(api.get).mockResolvedValue(response());
    vi.mocked(api.post).mockResolvedValue({ data: { success: true } });
    vi.mocked(api.patch).mockResolvedValue({ data: { success: true } });
  });

  it('localizes staff roles', async () => {
    renderPage();

    expect(await screen.findByText('Администратор')).toBeTruthy();
    expect(screen.queryByText('ADMIN')).toBeNull();
  });

  it('shows a dedicated empty state', async () => {
    vi.mocked(api.get).mockResolvedValue(response([]));
    renderPage();

    expect(await screen.findByText('Нет сотрудников')).toBeTruthy();
  });

  it('shows a load error and retries explicitly', async () => {
    vi.mocked(api.get)
      .mockRejectedValueOnce(new Error('identity-service unavailable'))
      .mockResolvedValueOnce(response());
    const user = userEvent.setup();
    renderPage();

    expect(await screen.findByText('Ошибка загрузки сотрудников')).toBeTruthy();
    await user.click(screen.getByRole('button', { name: 'Повторить' }));
    expect(await screen.findByText('admin@topdim.uz')).toBeTruthy();
  });

  it('does not submit a weak password that backend would reject', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('admin@topdim.uz');

    await user.click(screen.getByText('Добавить сотрудника').closest('button') as HTMLButtonElement);
    await user.type(screen.getByLabelText('Email'), 'new.admin@topdim.uz');
    await user.type(screen.getByLabelText('Пароль'), 'abcdef');
    await user.type(screen.getByLabelText('Имя'), 'New');
    await user.type(screen.getByLabelText('Телефон'), '+998901112233');
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    expect((await screen.findAllByText(/8–128 символов/)).length).toBeGreaterThan(0);
    await waitFor(() => expect(api.post).not.toHaveBeenCalled());
  });

  it('shows the backend reason for a common password rejected by the blocklist', async () => {
    vi.mocked(api.post).mockRejectedValue({
      response: {
        data: {
          message: 'Ошибка валидации',
          data: { password: 'Этот пароль слишком распространённый. Выберите более надёжный пароль' },
        },
      },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('admin@topdim.uz');

    await user.click(screen.getByText('Добавить сотрудника').closest('button') as HTMLButtonElement);
    await user.type(screen.getByLabelText('Email'), 'new.admin@topdim.uz');
    await user.type(screen.getByLabelText('Пароль'), 'Admin123!');
    await user.type(screen.getByLabelText('Имя'), 'New');
    await user.type(screen.getByLabelText('Телефон'), '+998901112233');
    await user.click(screen.getByRole('button', { name: 'Создать' }));

    expect(await screen.findByText('Этот пароль слишком распространённый. Выберите более надёжный пароль')).toBeTruthy();
  });
});
