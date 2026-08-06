import { beforeEach, describe, expect, it, vi } from 'vitest';
import api from '../../api/client';
import {
  createCategory,
  deleteCategory,
  getAdminCategories,
  updateCategory,
  type CategoryPayload,
} from './categoriesApi';

vi.mock('../../api/client', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

describe('categoriesApi', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads the full admin category list instead of the public active-only list', async () => {
    vi.mocked(api.get).mockResolvedValue({ data: { data: [{ id: 1, active: false }] } });

    await expect(getAdminCategories()).resolves.toEqual([{ id: 1, active: false }]);
    expect(api.get).toHaveBeenCalledWith('/api/v1/admin/categories');
  });

  it('uses the admin CRUD contracts with the complete category payload', async () => {
    const payload: CategoryPayload = {
      name: 'Красота',
      nameUz: "Go'zallik",
      slug: 'beauty',
      iconUrl: '/api/v1/media/beauty.png',
      sortOrder: 2,
      active: false,
    };
    vi.mocked(api.post).mockResolvedValue({ data: { data: 11 } });
    vi.mocked(api.put).mockResolvedValue({ data: { data: { id: 11, ...payload } } });
    vi.mocked(api.delete).mockResolvedValue({ data: { success: true } });

    await createCategory(payload);
    await updateCategory(11, payload);
    await deleteCategory(11);

    expect(api.post).toHaveBeenCalledWith('/api/v1/admin/categories', payload);
    expect(api.put).toHaveBeenCalledWith('/api/v1/admin/categories/11', payload);
    expect(api.delete).toHaveBeenCalledWith('/api/v1/admin/categories/11');
  });
});
