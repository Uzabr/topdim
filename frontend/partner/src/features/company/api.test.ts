import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api';
import { companyApi } from './api';

vi.mock('../../api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const mockedApi = vi.mocked(api);
const timestamp = '2026-08-13T12:00:00';

function success<T>(data: T, message: string | null = null) {
  return { data: { success: true, message, data, timestamp } };
}

describe('companyApi contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads the currently published merchant profile', async () => {
    const profile = {
      id: 8,
      name: 'Market',
      active: true,
      profileVersion: 3,
      publicationReady: true,
      publicationBlockReason: null,
      primaryLocation: null,
      locations: [],
    };
    mockedApi.get.mockResolvedValue(success(profile));

    await expect(companyApi.getPublishedProfile()).resolves.toEqual(profile);
    expect(mockedApi.get).toHaveBeenCalledWith('/api/v1/partner/merchant');
  });

  it('lists change requests with status and server pagination', async () => {
    const page = {
      content: [],
      number: 2,
      size: 10,
      totalElements: 0,
      totalPages: 0,
      first: false,
      last: true,
      empty: true,
    };
    mockedApi.get.mockResolvedValue(success(page));

    await expect(companyApi.listRequests({
      status: 'REVISION_REQUESTED',
      page: 2,
      size: 10,
    })).resolves.toEqual(page);
    expect(mockedApi.get).toHaveBeenCalledWith(
      '/api/v1/partner/merchant/change-requests',
      { params: { status: 'REVISION_REQUESTED', page: 2, size: 10 } },
    );
  });

  it('creates, updates and submits a draft through partner endpoints', async () => {
    const draft = { id: 17, status: 'DRAFT' };
    mockedApi.post.mockResolvedValue(success(draft, 'Черновик создан'));
    mockedApi.put.mockResolvedValue(success(draft, 'Черновик обновлён'));

    await expect(companyApi.createDraft()).resolves.toEqual(draft);
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      1,
      '/api/v1/partner/merchant/change-requests',
    );

    const payload = {
      name: 'Market',
      description: null,
      logoUrl: null,
      coverUrl: null,
      email: null,
      website: null,
      contactPerson: null,
      locations: [],
    };
    await companyApi.updateRequest(17, payload);
    expect(mockedApi.put).toHaveBeenCalledWith(
      '/api/v1/partner/merchant/change-requests/17',
      payload,
    );

    await companyApi.submit(17);
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      2,
      '/api/v1/partner/merchant/change-requests/17/submit',
    );
  });

  it('loads one change request by its server id', async () => {
    const request = { id: 17, status: 'IN_REVIEW' };
    mockedApi.get.mockResolvedValue(success(request));

    await expect(companyApi.getRequest(17)).resolves.toEqual(request);
    expect(mockedApi.get).toHaveBeenCalledWith(
      '/api/v1/partner/merchant/change-requests/17',
    );
  });

  it('sends a nonblank withdrawal reason and can copy or delete a request', async () => {
    mockedApi.post.mockResolvedValue(success({ id: 18 }));
    mockedApi.delete.mockResolvedValue({ status: 204 });

    await companyApi.withdraw(17, 'Компания временно закрыта');
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      1,
      '/api/v1/partner/merchant/change-requests/17/withdraw',
      { reason: 'Компания временно закрыта' },
    );

    await companyApi.copy(17);
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      2,
      '/api/v1/partner/merchant/change-requests/17/copy',
    );

    await expect(companyApi.deleteDraft(18)).resolves.toBeUndefined();
    expect(mockedApi.delete).toHaveBeenCalledWith(
      '/api/v1/partner/merchant/change-requests/18',
    );
  });

  it('uploads media as FormData without overriding the multipart boundary', async () => {
    mockedApi.post.mockResolvedValue(success(
      { fileName: 'logo.png', url: '/api/v1/media/logo.png' },
      'Файл загружен',
    ));
    const file = new File(['logo'], 'logo.png', { type: 'image/png' });

    await expect(companyApi.uploadMedia(file)).resolves.toEqual({
      fileName: 'logo.png',
      url: '/api/v1/media/logo.png',
    });
    const [url, body, config] = mockedApi.post.mock.calls[0];
    expect(url).toBe('/api/v1/media/upload');
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('file')).toBe(file);
    expect(config).toBeUndefined();
  });
});
