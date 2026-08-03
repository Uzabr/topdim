export type CouponTab =
  | 'new'
  | 'in-progress'
  | 'revision'
  | 'waiting-partner'
  | 'published'
  | 'archived';

export type CouponView = 'table' | 'kanban';

export type CouponStatus =
  | 'LEAD'
  | 'DRAFT'
  | 'REVISION_REQUESTED'
  | 'WAITING_FOR_MERCHANT'
  | 'ACTIVE'
  | 'PAUSED'
  | 'SOLD_OUT'
  | 'ARCHIVED';

export type CouponAction =
  | 'view'
  | 'take-to-work'
  | 'edit'
  | 'send-to-approval'
  | 'support-review'
  | 'pause'
  | 'restore'
  | 'archive';

export type CouponPageSize = 20 | 50 | 100;
export type StaffRole = 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN';

export interface StaffUser {
  id: number;
  role: StaffRole;
}

export interface AdminCouponRow {
  id: number;
  title: string;
  status: CouponStatus;
  assignedModeratorId: number | null;
  assignedModeratorName: string | null;
}

export interface CouponWorkspaceState {
  tab: CouponTab;
  view: CouponView;
  search: string;
  merchantId: number | null;
  assignedModeratorId: number | null;
  page: number;
  pageSize: CouponPageSize;
}

export type CouponWorkspacePatch = Partial<CouponWorkspaceState>;
