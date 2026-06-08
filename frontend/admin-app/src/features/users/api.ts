import api from '../../api/client';

export interface AdminUser {
  id: number;
  email: string;
  phone: string | null;
  firstName: string;
  lastName: string | null;
  role: string;
  enabled: boolean;
  emailVerified: boolean;
  phoneVerified: boolean;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface ApiResponse<T> {
  success: boolean;
  data: T;
  message?: string;
}

/** Paginated user list with search/role filter */
export const fetchUsersPage = async (params: {
  page?: number;
  size?: number;
  search?: string;
  role?: string;
}) => {
  const res = await api.get<ApiResponse<PageResponse<AdminUser>>>(
    '/api/v1/admin/users',
    { params },
  );
  return res.data.data;
};

/** Block/unblock user */
export const blockUser = async (id: number, blocked: boolean) => {
  const res = await api.patch<ApiResponse<AdminUser>>(
    `/api/v1/admin/users/${id}/block`,
    { blocked },
  );
  return res.data.data;
};
