import type { PageResponse } from '../../types';

export type MerchantProfileChangeStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'IN_REVIEW'
  | 'REVISION_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'WITHDRAWN'
  | 'OUTDATED';

export type ModerationQueueStatus = Exclude<MerchantProfileChangeStatus, 'DRAFT'>;

export interface MerchantProfileChangeSummary {
  id: number;
  merchantId: number;
  name: string;
  status: MerchantProfileChangeStatus;
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

export interface MerchantProfileLocationSnapshot {
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

export interface MerchantProfileChangeDetail {
  id: number;
  merchantId: number;
  authorUserId: number;
  authorStaffId: number | null;
  authorRole: string;
  baseProfileVersion: number;
  status: MerchantProfileChangeStatus;
  assigneeUserId: number | null;
  moderationComment: string | null;
  name: string;
  description: string | null;
  logoUrl: string | null;
  coverUrl: string | null;
  email: string | null;
  website: string | null;
  contactPerson: string | null;
  locations: MerchantProfileLocationSnapshot[];
  createdAt: string;
  updatedAt: string;
  submittedAt: string | null;
  assignedAt: string | null;
  decidedAt: string | null;
  withdrawnAt: string | null;
  lockVersion: number;
}

export interface PublishedMerchantLocation {
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

export interface PublishedMerchantProfile {
  id: number;
  name: string;
  description: string | null;
  logoUrl: string | null;
  coverUrl: string | null;
  email: string | null;
  website: string | null;
  contactPerson: string | null;
  active: boolean;
  profileVersion: number;
  locations: PublishedMerchantLocation[];
}

export type MerchantProfileChangePage = PageResponse<MerchantProfileChangeSummary>;

export interface MerchantProfileChangeQueueFilters {
  status?: ModerationQueueStatus;
  search?: string;
  assigneeUserId?: number;
  submittedFrom?: string;
  submittedTo?: string;
  page: number;
  size: number;
}
