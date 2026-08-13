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
