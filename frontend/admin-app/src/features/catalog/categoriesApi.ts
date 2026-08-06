import api from '../../api/client';

export interface AdminCategory {
  id: number;
  name: string;
  nameUz: string | null;
  slug: string;
  iconUrl: string | null;
  sortOrder: number;
  active: boolean;
}

export interface CategoryPayload {
  name: string;
  nameUz: string;
  slug: string;
  iconUrl: string;
  sortOrder: number;
  active: boolean;
}

export async function getAdminCategories(): Promise<AdminCategory[]> {
  const response = await api.get('/api/v1/admin/categories');
  return response.data.data;
}

export async function createCategory(payload: CategoryPayload): Promise<number> {
  const response = await api.post('/api/v1/admin/categories', payload);
  return response.data.data;
}

export async function updateCategory(
  id: number,
  payload: CategoryPayload,
): Promise<AdminCategory> {
  const response = await api.put(`/api/v1/admin/categories/${id}`, payload);
  return response.data.data;
}

export async function deleteCategory(id: number): Promise<void> {
  await api.delete(`/api/v1/admin/categories/${id}`);
}
