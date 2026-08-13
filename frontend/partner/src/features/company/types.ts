export type CompanyProfileChangeStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'IN_REVIEW'
  | 'REVISION_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'OUTDATED';

export interface CompanyLocation {
  id: number;
  title: string | null;
  address: string | null;
  phone: string | null;
  workingHours: string | null;
  latitude: number | null;
  longitude: number | null;
  primary: boolean;
  active: boolean;
}

export interface PublishedCompanyProfile {
  id: number;
  name: string;
  description: string | null;
  logoUrl: string | null;
  coverUrl: string | null;
  email: string | null;
  website: string | null;
  contactPerson: string | null;
  userId: number | null;
  active: boolean;
  profileVersion: number;
  publicationReady: boolean;
  publicationBlockReason: string | null;
  primaryLocation: CompanyLocation | null;
  locations: CompanyLocation[];
}

export interface CompanyChangeLocation {
  id: number;
  sourceLocationId: number | null;
  title: string | null;
  address: string | null;
  phone: string | null;
  workingHours: string | null;
  latitude: number | null;
  longitude: number | null;
  primary: boolean;
  active: boolean;
  sortOrder: number;
}

export interface CompanyLocationPayload {
  sourceLocationId: number | null;
  title: string | null;
  address: string | null;
  phone: string | null;
  workingHours: string | null;
  latitude: number | null;
  longitude: number | null;
  primary: boolean;
  active: boolean;
}

export interface CompanyChangePayload {
  name: string;
  description: string | null;
  logoUrl: string | null;
  coverUrl: string | null;
  email: string | null;
  website: string | null;
  contactPerson: string | null;
  locations: CompanyLocationPayload[];
}

export interface CompanyChangeSummary {
  id: number;
  merchantId: number;
  name: string;
  status: CompanyProfileChangeStatus;
  baseProfileVersion: number;
  authorUserId: number;
  authorStaffId: number | null;
  authorRole: string;
  assigneeUserId: number | null;
  moderationComment: string | null;
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
  assignedAt: string | null;
}

export interface CompanyChangeRequest extends CompanyChangeSummary {
  logoUrl: string | null;
  coverUrl: string | null;
  description: string | null;
  email: string | null;
  website: string | null;
  contactPerson: string | null;
  locations: CompanyChangeLocation[];
  decidedAt: string | null;
  withdrawnAt: string | null;
  lockVersion: number;
}

export interface PageResponse<T> {
  content: T[];
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
  empty: boolean;
}

export interface ApiResponse<T> {
  success: boolean;
  message: string | null;
  data: T;
  timestamp: string;
}

export interface MediaUploadResponse {
  fileName: string;
  url: string;
}

export interface CompanyRequestFilters {
  status?: CompanyProfileChangeStatus;
  page?: number;
  size?: number;
}
