import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useAuthStore } from '../../store/authStore';
import * as profileChangesApi from './api';
import { MerchantProfileChangeDetailPage } from './MerchantProfileChangeDetailPage';
import type { MerchantProfileChangeDetail, PublishedMerchantProfile } from './types';
import type { UserRole } from '../../types';

vi.mock('./api', () => ({
  MERCHANT_PROFILE_CHANGES_QUERY_KEY: ['merchant-profile-changes'],
  fetchMerchantProfileChangeDetail: vi.fn(),
  fetchMerchantProfileChangeHistory: vi.fn(),
  fetchModerationAssignees: vi.fn(),
  fetchPublishedMerchantProfile: vi.fn(),
  approveMerchantProfileChange: vi.fn(),
  requestMerchantProfileChangeRevision: vi.fn(),
  rejectMerchantProfileChange: vi.fn(),
  releaseMerchantProfileChange: vi.fn(),
  reassignMerchantProfileChange: vi.fn(),
}));

const request: MerchantProfileChangeDetail = {
  id: 71,
  merchantId: 9,
  authorUserId: 18,
  authorStaffId: 24,
  authorRole: 'MANAGER',
  baseProfileVersion: 3,
  status: 'IN_REVIEW',
  assigneeUserId: 42,
  moderationComment: null,
  name: 'Safia Cafe',
  description: 'Новое описание',
  logoUrl: null,
  coverUrl: 'https://cdn.test/new-cover.jpg',
  email: 'office@safia.test',
  website: 'https://safia.test',
  contactPerson: 'Алия',
  locations: [{
    id: 701,
    sourceLocationId: 11,
    title: 'Чиланзар',
    address: 'ул. Катартал, 1',
    phone: '+998901111111',
    workingHours: '09:00-22:00',
    latitude: 41.27,
    longitude: 69.20,
    primary: false,
    active: false,
    sortOrder: 0,
  }],
  createdAt: '2026-08-10T09:00:00',
  updatedAt: '2026-08-11T10:00:00',
  submittedAt: '2026-08-11T10:00:00',
  assignedAt: '2026-08-12T10:00:00',
  decidedAt: null,
  withdrawnAt: null,
  lockVersion: 2,
};

const published: PublishedMerchantProfile = {
  id: 9,
  name: 'Safia',
  description: 'Старое описание',
  logoUrl: 'https://cdn.test/logo.jpg',
  coverUrl: null,
  email: 'old@safia.test',
  website: null,
  contactPerson: 'Алия',
  active: true,
  profileVersion: 3,
  locations: [{
    id: 11,
    title: 'Чиланзар',
    address: 'ул. Катартал, 1',
    phone: '+998901111111',
    workingHours: '09:00-22:00',
    latitude: 41.27,
    longitude: 69.20,
    primary: true,
    active: true,
  }],
};

function loginAs(id: number, role: UserRole) {
  useAuthStore.getState().login('access-token', {
    id,
    email: `${role.toLowerCase()}@topdim.uz`,
    phone: '+998901234567',
    firstName: role,
    lastName: 'Tester',
    role,
    avatarUrl: null,
  });
}

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <MemoryRouter initialEntries={['/merchants/profile-changes/71']}>
      <QueryClientProvider client={queryClient}>
        <Routes>
          <Route
            path="/merchants/profile-changes/:id"
            element={<MerchantProfileChangeDetailPage />}
          />
        </Routes>
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

describe('MerchantProfileChangeDetailPage', () => {
  beforeEach(() => {
    loginAs(42, 'MODERATOR');
    vi.mocked(profileChangesApi.fetchMerchantProfileChangeDetail).mockResolvedValue(request);
    vi.mocked(profileChangesApi.fetchPublishedMerchantProfile).mockResolvedValue(published);
    vi.mocked(profileChangesApi.fetchMerchantProfileChangeHistory).mockResolvedValue([
      {
        id: 1,
        previousStatus: 'PENDING_REVIEW',
        newStatus: 'IN_REVIEW',
        actorUserId: 42,
        actorRole: 'MODERATOR',
        comment: 'Взята в работу',
        createdAt: '2026-08-12T10:00:00',
      },
    ]);
    vi.mocked(profileChangesApi.fetchModerationAssignees).mockResolvedValue([
      { userId: 88, name: 'Ali Valiyev', email: 'ali@topdim.uz', role: 'MODERATOR' },
      { userId: 18, name: 'Request Author', email: 'author@topdim.uz', role: 'ADMIN' },
    ]);
    vi.mocked(profileChangesApi.approveMerchantProfileChange).mockResolvedValue();
    vi.mocked(profileChangesApi.requestMerchantProfileChangeRevision).mockResolvedValue();
    vi.mocked(profileChangesApi.rejectMerchantProfileChange).mockResolvedValue();
    vi.mocked(profileChangesApi.releaseMerchantProfileChange).mockResolvedValue();
    vi.mocked(profileChangesApi.reassignMerchantProfileChange).mockResolvedValue();
  });

  it('shows metadata, scalar comparison and semantic location changes', async () => {
    renderPage();

    expect(await screen.findByRole('heading', { name: 'Изменение компании #71' })).toBeTruthy();
    expect(screen.getAllByText('Сейчас').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Предлагается').length).toBeGreaterThan(0);
    expect(screen.getByText('Safia')).toBeTruthy();
    expect(screen.getByText('Safia Cafe')).toBeTruthy();
    expect(screen.getByText('Филиал будет отключён')).toBeTruthy();
    expect(document.body.textContent).toContain('User ID: 18 · MANAGER');
    expect(screen.getAllByText('Версия 3')).toHaveLength(2);
    expect(screen.getByText('История заявки')).toBeTruthy();
    expect(screen.getByText('Ожидает проверки → В работе')).toBeTruthy();
    expect(screen.getByText('MODERATOR · User ID: 42')).toBeTruthy();
    expect(screen.getByText('Взята в работу')).toBeTruthy();
  });

  it('requires a comment before returning the request for revision', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Safia Cafe');

    await user.click(screen.getByRole('button', { name: 'Вернуть на доработку' }));
    await user.click(screen.getByRole('button', { name: 'Подтвердить' }));

    expect(await screen.findByText('Укажите комментарий')).toBeTruthy();
    expect(profileChangesApi.requestMerchantProfileChangeRevision).not.toHaveBeenCalled();
  });

  it('submits a trimmed revision comment through the assigned reviewer action', async () => {
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Safia Cafe');

    await user.click(screen.getByRole('button', { name: 'Вернуть на доработку' }));
    await user.type(screen.getByLabelText('Комментарий'), '  Уточните адрес  ');
    await user.click(screen.getByRole('button', { name: 'Подтвердить' }));

    await waitFor(() => {
      expect(profileChangesApi.requestMerchantProfileChangeRevision)
        .toHaveBeenCalledWith(71, 'Уточните адрес');
    });
    expect(await screen.findByText('Заявка возвращена на доработку')).toBeTruthy();
  });

  it('hides release and reassignment from MODERATOR but exposes them to ADMIN', async () => {
    const first = renderPage();
    await screen.findByText('Safia Cafe');
    expect(screen.queryByRole('button', { name: 'Освободить' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Переназначить' })).toBeNull();
    first.unmount();

    loginAs(42, 'ADMIN');
    renderPage();
    await screen.findByText('Safia Cafe');
    expect(screen.getByRole('button', { name: 'Освободить' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Переназначить' })).toBeTruthy();
  });

  it('lets ADMIN choose an eligible non-author assignee by name', async () => {
    loginAs(42, 'ADMIN');
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Safia Cafe');

    await user.click(screen.getByRole('button', { name: 'Переназначить' }));
    expect(screen.queryByText('Request Author')).toBeNull();
    await user.click(screen.getByLabelText('Новый исполнитель'));
    await user.click(await screen.findByText('Ali Valiyev · MODERATOR'));
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Переназначить' }));

    await waitFor(() => {
      expect(profileChangesApi.reassignMerchantProfileChange).toHaveBeenCalledWith(71, 88);
    });
  });

  it('shows retryable error when eligible assignees cannot be loaded', async () => {
    loginAs(42, 'ADMIN');
    vi.mocked(profileChangesApi.fetchModerationAssignees).mockRejectedValue(new Error('offline'));
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Safia Cafe');

    await user.click(screen.getByRole('button', { name: 'Переназначить' }));

    expect(await screen.findByText('Не удалось загрузить список исполнителей')).toBeTruthy();
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Переназначить' }))
      .toHaveProperty('disabled', true);
  });

  it('does not let the author decide their own assigned request', async () => {
    loginAs(18, 'MODERATOR');
    vi.mocked(profileChangesApi.fetchMerchantProfileChangeDetail).mockResolvedValue({
      ...request,
      assigneeUserId: 18,
    });
    renderPage();

    expect(await screen.findByText('Автор не может принять решение по собственной заявке')).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Одобрить' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Вернуть на доработку' })).toBeNull();
    expect(screen.queryByRole('button', { name: 'Отклонить' })).toBeNull();
  });

  it('refreshes stale detail after a 409 decision conflict', async () => {
    vi.mocked(profileChangesApi.approveMerchantProfileChange).mockRejectedValue({
      response: { status: 409, data: { message: 'Заявка уже изменена другим сотрудником' } },
    });
    const user = userEvent.setup();
    renderPage();
    await screen.findByText('Safia Cafe');

    await user.click(screen.getByRole('button', { name: 'Одобрить' }));
    await user.click(screen.getByRole('button', { name: 'Подтвердить' }));

    expect(await screen.findByText('Заявка уже изменена')).toBeTruthy();
    await waitFor(() => {
      expect(profileChangesApi.fetchMerchantProfileChangeDetail).toHaveBeenCalledTimes(2);
    });
  });
});
