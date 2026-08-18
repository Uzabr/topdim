import { App as AntApp } from 'antd';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { companyApi } from './api';
import CompanyRequestEditorPage from './CompanyRequestEditorPage';
import type { CompanyChangeRequest } from './types';

vi.mock('./api', () => ({
  companyApi: {
    getPublishedProfile: vi.fn(),
    listRequests: vi.fn(),
    createDraft: vi.fn(),
    getRequest: vi.fn(),
    updateRequest: vi.fn(),
    deleteDraft: vi.fn(),
    submit: vi.fn(),
    withdraw: vi.fn(),
    copy: vi.fn(),
    uploadMedia: vi.fn(),
  },
}));

const mockedCompanyApi = vi.mocked(companyApi);

const request: CompanyChangeRequest = {
  id: 17,
  merchantId: 8,
  name: 'Market',
  status: 'REVISION_REQUESTED',
  baseProfileVersion: 3,
  authorUserId: 41,
  authorStaffId: null,
  authorRole: 'OWNER',
  assigneeUserId: 7,
  moderationComment: 'Добавьте телефон второго филиала',
  createdAt: '2026-08-13T09:00:00',
  updatedAt: '2026-08-13T12:00:00',
  submittedAt: '2026-08-13T10:00:00',
  assignedAt: '2026-08-13T11:00:00',
  logoUrl: 'https://cdn.topdim.uz/old-logo.png',
  coverUrl: 'https://cdn.topdim.uz/old-cover.jpg',
  description: 'Семейный магазин',
  email: 'owner@market.uz',
  website: 'https://market.uz',
  contactPerson: 'Азиза',
  locations: [
    {
      id: 701,
      sourceLocationId: 71,
      title: 'Главный филиал',
      address: 'Ташкент, ул. Амира Темура, 1',
      phone: '+998 90 111 22 33',
      workingHours: '09:00–21:00',
      latitude: 41.311,
      longitude: 69.279,
      primary: true,
      active: true,
      sortOrder: 0,
    },
    {
      id: 702,
      sourceLocationId: 72,
      title: 'Второй филиал',
      address: 'Ташкент, ул. Навои, 12',
      phone: '+998 90 444 55 66',
      workingHours: '10:00–20:00',
      latitude: null,
      longitude: null,
      primary: false,
      active: true,
      sortOrder: 1,
    },
  ],
  decidedAt: null,
  withdrawnAt: null,
  lockVersion: 2,
};

function renderEditor(value: CompanyChangeRequest = request) {
  mockedCompanyApi.getRequest.mockResolvedValue(value);
  mockedCompanyApi.updateRequest.mockImplementation(async (_id, payload) => ({
    ...value,
    ...payload,
    locations: value.locations.map((location, index) => ({
      ...location,
      ...payload.locations[index],
    })),
  }));
  mockedCompanyApi.submit.mockResolvedValue({ ...value, status: 'PENDING_REVIEW' });
  mockedCompanyApi.copy.mockResolvedValue({ ...value, id: 99, status: 'DRAFT' });

  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const router = createMemoryRouter([
    { path: '/company/requests/99', element: <div>Новая копия открыта</div> },
    { path: '/company/requests/:id', element: <CompanyRequestEditorPage /> },
    { path: '/company', element: <div>Профиль компании открыт</div> },
  ], { initialEntries: ['/company/requests/17'] });
  const rendered = render(
    <QueryClientProvider client={queryClient}>
      <AntApp>
        <RouterProvider router={router} />
      </AntApp>
    </QueryClientProvider>,
  );
  return { ...rendered, router };
}

describe('CompanyRequestEditorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedCompanyApi.uploadMedia.mockResolvedValue({
      fileName: 'logo.png',
      url: 'https://cdn.topdim.uz/new-logo.png',
    });
  });

  it('uploads optional media, clears both images and saves explicit null values', async () => {
    renderEditor();

    expect(await screen.findByText('Добавьте телефон второго филиала')).toBeTruthy();
    expect(screen.getByText('Нужны исправления')).toBeTruthy();
    const logo = new File(['image'], 'logo.png', { type: 'image/png' });
    fireEvent.change(screen.getByLabelText('Загрузить логотип'), {
      target: { files: [logo] },
    });
    expect((await screen.findByRole('img', {
      name: 'Логотип компании Market',
    }) as HTMLImageElement).src).toBe('https://cdn.topdim.uz/new-logo.png');

    fireEvent.change(screen.getByLabelText('Название компании'), {
      target: { value: '  Market после модерации  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Убрать логотип' }));
    fireEvent.click(screen.getByRole('button', { name: 'Убрать обложку' }));
    fireEvent.click(screen.getByRole('button', { name: /Сохранить черновик/ }));

    await waitFor(() => expect(mockedCompanyApi.updateRequest).toHaveBeenCalledWith(
      17,
      expect.objectContaining({ logoUrl: null, coverUrl: null }),
    ));
    expect((screen.getByLabelText('Название компании') as HTMLInputElement).value)
      .toBe('Market после модерации');
  });

  it('submits only a saved snapshot with exactly one valid active primary location', async () => {
    const noPrimary = {
      ...request,
      status: 'DRAFT' as const,
      moderationComment: null,
      locations: request.locations.map((location, index) => ({
        ...location,
        primary: false,
        latitude: index === 0 ? 91 : location.latitude,
      })),
    };
    renderEditor(noPrimary);

    const submit = await screen.findByRole('button', { name: /Отправить на проверку/ }) as HTMLButtonElement;
    expect(submit.disabled).toBe(true);
    expect(screen.getByText('Выберите ровно один активный основной филиал')).toBeTruthy();

    fireEvent.click(screen.getAllByRole('button', { name: /Сделать основным/ })[0]);
    expect(await screen.findByText('Филиал 1: широта должна быть от -90 до 90')).toBeTruthy();
    expect(submit.disabled).toBe(true);
    expect((screen.getByRole('button', {
      name: /Сохранить черновик/,
    }) as HTMLButtonElement).disabled).toBe(true);
    fireEvent.change(screen.getAllByRole('spinbutton', { name: 'Широта' })[0], {
      target: { value: '41.311' },
    });
    await waitFor(() => expect(submit.disabled).toBe(false));
    fireEvent.click(submit);

    await waitFor(() => expect(mockedCompanyApi.updateRequest).toHaveBeenCalledOnce());
    expect(mockedCompanyApi.submit).toHaveBeenCalledWith(17);
    expect(await screen.findByText('Профиль компании открыт')).toBeTruthy();
  });

  it('keeps the editor clean when saving succeeded but submission failed', async () => {
    renderEditor({ ...request, status: 'DRAFT', moderationComment: null });
    mockedCompanyApi.submit.mockRejectedValue(new Error('moderation unavailable'));

    const name = await screen.findByLabelText('Название компании');
    fireEvent.change(name, { target: { value: 'Market после сохранения' } });
    fireEvent.click(await screen.findByRole('button', { name: /Отправить на проверку/ }));

    await waitFor(() => expect(mockedCompanyApi.updateRequest).toHaveBeenCalledOnce());
    await waitFor(() => expect(mockedCompanyApi.submit).toHaveBeenCalledOnce());
    expect(screen.queryByText('Есть несохранённые изменения')).toBeNull();
    const beforeUnload = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(beforeUnload);
    expect(beforeUnload.defaultPrevented).toBe(false);
  });

  it('locks the snapshot while it is being saved for submission', async () => {
    let finishSave: ((value: CompanyChangeRequest) => void) | undefined;
    renderEditor({ ...request, status: 'DRAFT', moderationComment: null });
    mockedCompanyApi.updateRequest.mockImplementation(() => new Promise((resolve) => {
      finishSave = resolve;
    }));

    fireEvent.click(await screen.findByRole('button', { name: /Отправить на проверку/ }));

    await waitFor(() => expect(mockedCompanyApi.updateRequest).toHaveBeenCalledOnce());
    expect((screen.getByLabelText('Название компании') as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByLabelText('Загрузить логотип') as HTMLInputElement).disabled).toBe(true);
    expect((screen.getByRole('button', { name: /Добавить филиал/ }) as HTMLButtonElement).disabled)
      .toBe(true);

    finishSave?.(request);
    await waitFor(() => expect(mockedCompanyApi.submit).toHaveBeenCalledOnce());
  });

  it('renders OUTDATED as read-only and creates an editable copy', async () => {
    renderEditor({ ...request, status: 'OUTDATED', moderationComment: 'Профиль уже обновлён' });

    expect(await screen.findByText('Заявка устарела')).toBeTruthy();
    expect((screen.getByLabelText('Название компании') as HTMLInputElement).disabled).toBe(true);
    expect(screen.queryByRole('button', { name: /Сохранить черновик/ })).toBeNull();

    fireEvent.click(screen.getByRole('button', { name: /Создать копию/ }));

    expect(await screen.findByText('Новая копия открыта')).toBeTruthy();
  });

  it('shows live preview and warns before leaving with unsaved changes', async () => {
    const { router } = renderEditor({ ...request, status: 'DRAFT', moderationComment: null });

    const name = await screen.findByLabelText('Название компании');
    fireEvent.change(name, { target: { value: 'Новый Market' } });
    fireEvent.click(screen.getByRole('tab', { name: 'Предпросмотр' }));

    expect(await screen.findByRole('heading', { name: 'Новый Market' })).toBeTruthy();
    const beforeUnload = new Event('beforeunload', { cancelable: true });
    window.dispatchEvent(beforeUnload);
    expect(beforeUnload.defaultPrevented).toBe(true);

    void router.navigate('/company');

    expect(await screen.findByRole('dialog', {
      name: 'Есть несохранённые изменения',
    })).toBeTruthy();
    expect(router.state.location.pathname).toBe('/company/requests/17');
    fireEvent.click(screen.getByRole('button', { name: 'Остаться' }));
    expect(router.state.location.pathname).toBe('/company/requests/17');

    void router.navigate('/company');
    fireEvent.click(await screen.findByRole('button', { name: 'Выйти без сохранения' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/company'));
  });
});
