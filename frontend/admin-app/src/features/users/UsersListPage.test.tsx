import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { App as AntApp } from 'antd';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { UsersListPage } from './UsersListPage';
import { blockUser, fetchUsersPage, type AdminUser } from './api';
import { useAuthStore } from '../../store/authStore';
import type { UserRole } from '../../types';

vi.mock('./api', () => ({
  blockUser: vi.fn(),
  fetchUsersPage: vi.fn(),
}));

function loginAs(role: UserRole) {
  useAuthStore.getState().login('token', {
    id: 1,
    email: `${role.toLowerCase()}@topdim.uz`,
    phone: '+998901234567',
    firstName: role,
    lastName: 'Tester',
    role,
    avatarUrl: null,
  });
}

const ordinaryUser: AdminUser = {
  id: 7,
  email: 'user@topdim.uz',
  phone: '+998901234567',
  firstName: 'Ali',
  lastName: 'Valiyev',
  role: 'USER',
  enabled: true,
  emailVerified: true,
  phoneVerified: false,
  createdAt: '2026-08-04T10:30:00',
};

const adminUser: AdminUser = {
  ...ordinaryUser,
  id: 8,
  email: 'admin@topdim.uz',
  firstName: 'Admin',
  role: 'ADMIN',
};

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <UsersListPage />
      </AntApp>
    </QueryClientProvider>,
  );
}

describe('UsersListPage', () => {
  beforeEach(() => {
    loginAs('ADMIN');
    vi.mocked(fetchUsersPage).mockResolvedValue({
      content: [ordinaryUser, adminUser],
      totalElements: 2,
      totalPages: 1,
      number: 0,
      size: 20,
    });
    vi.mocked(blockUser).mockResolvedValue({ ...ordinaryUser, enabled: false });
  });

  it('hides the actions column for roles other than ADMIN and SUPER_ADMIN', async () => {
    loginAs('MODERATOR');
    renderPage();
    await screen.findByText('user@topdim.uz');

    expect(screen.queryByText('Действия')).toBeNull();
    expect(screen.queryByRole('button', { name: 'Заблокировать' })).toBeNull();
  });

  it('sends search and role together instead of dropping one filter', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('user@topdim.uz');

    await user.type(screen.getByPlaceholderText('Поиск по имени, email, телефону...'), '99890');
    await user.click(screen.getByRole('combobox'));
    await user.click(await screen.findByText('Партнёры'));

    await waitFor(() => {
      expect(fetchUsersPage).toHaveBeenLastCalledWith({
        page: 0,
        size: 20,
        search: '99890',
        role: 'PARTNER',
      });
    });
  });

  it('shows a load error instead of an empty user table', async () => {
    vi.mocked(fetchUsersPage).mockRejectedValue(new Error('identity-service unavailable'));

    renderPage();

    expect(await screen.findByText('Ошибка загрузки пользователей')).toBeTruthy();
  });

  it('does not offer a forbidden block action for administrator accounts', async () => {
    renderPage();
    const adminEmail = await screen.findByText('admin@topdim.uz');
    const adminRow = adminEmail.closest('tr');

    expect(adminRow).not.toBeNull();
    expect(adminRow?.textContent).toContain('Защищён');
    expect(adminRow?.textContent).not.toContain('Заблокировать');
  });

  it('requires confirmation before blocking an ordinary user', async () => {
    const user = userEvent.setup();
    renderPage();
    const ordinaryEmail = await screen.findByText('user@topdim.uz');
    const ordinaryRow = ordinaryEmail.closest('tr');
    const blockButton = ordinaryRow?.querySelector('button');

    expect(blockButton).not.toBeNull();
    await user.click(blockButton as HTMLButtonElement);

    expect(await screen.findByRole('dialog')).toBeTruthy();
    expect(screen.getAllByText('Заблокировать пользователя?')).not.toHaveLength(0);
    expect(blockUser).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: 'Заблокировать' }));

    await waitFor(() => expect(blockUser).toHaveBeenCalledWith(7, true));
  });
});
