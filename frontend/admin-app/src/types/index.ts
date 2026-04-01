export type UserRole = 'USER' | 'PARTNER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN';

export interface AuthUser {
  id: number;
  email: string;
  phone: string;
  firstName: string;
  lastName: string;
  role: UserRole;
  avatarUrl: string | null;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T;
  timestamp?: string;
}

export interface PageResponse<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
  };
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface PartnerApplication {
  id: number;
  firstName: string;
  lastName: string;
  phone: string;
  companyName: string;
  comment: string | null;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  createdAt: string;
  updatedAt: string | null;
}
