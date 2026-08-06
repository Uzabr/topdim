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
  email: string | null;
  companyName: string;
  city: string | null;
  address: string | null;
  workingHours: string | null;
  businessCategory: string | null;
  website: string | null;
  telegramUsername: string | null;
  comment: string | null;
  source: string;
  status: 'PENDING' | 'PROCESSING' | 'APPROVED' | 'REJECTED';
  rejectionReason: string | null;
  reviewedBy: number | null;
  reviewedAt: string | null;
  linkedUserId: number | null;
  linkedMerchantId: number | null;
  createdAt: string;
  updatedAt: string | null;
}
