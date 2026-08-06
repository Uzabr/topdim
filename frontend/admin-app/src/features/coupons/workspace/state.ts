import type {
  CouponPageSize,
  CouponStatus,
  CouponTab,
  CouponView,
  CouponWorkspacePatch,
  CouponWorkspaceState,
} from './types';

const DEFAULT_STATE: CouponWorkspaceState = {
  tab: 'new',
  view: 'table',
  search: '',
  merchantId: null,
  assignedModeratorId: null,
  page: 0,
  pageSize: 20,
};

const TABS = new Set<CouponTab>([
  'new',
  'in-progress',
  'revision',
  'waiting-partner',
  'published',
  'archived',
]);

const VIEWS = new Set<CouponView>(['table', 'kanban']);
const PAGE_SIZES = new Set<CouponPageSize>([20, 50, 100]);

const STATUSES_BY_TAB: Record<CouponTab, CouponStatus[]> = {
  new: ['LEAD'],
  'in-progress': ['DRAFT'],
  revision: ['REVISION_REQUESTED'],
  'waiting-partner': ['WAITING_FOR_MERCHANT'],
  published: ['ACTIVE', 'PAUSED', 'SOLD_OUT'],
  archived: ['ARCHIVED'],
};

function parseNonNegativeInteger(value: string | null): number | null {
  if (value === null || !/^\d+$/.test(value)) {
    return null;
  }

  const parsed = Number(value);
  return Number.isSafeInteger(parsed) ? parsed : null;
}

function parsePositiveInteger(value: string | null): number | null {
  const parsed = parseNonNegativeInteger(value);
  return parsed !== null && parsed > 0 ? parsed : null;
}

function normalizeState(state: CouponWorkspaceState): CouponWorkspaceState {
  return {
    ...state,
    search: state.search.trim(),
    merchantId: state.merchantId !== null && state.merchantId > 0
      ? state.merchantId
      : null,
    assignedModeratorId:
      state.assignedModeratorId !== null && state.assignedModeratorId > 0
        ? state.assignedModeratorId
        : null,
    page: Number.isSafeInteger(state.page) && state.page >= 0 ? state.page : 0,
    pageSize: PAGE_SIZES.has(state.pageSize) ? state.pageSize : 20,
  };
}

export function parseWorkspaceState(params: URLSearchParams): CouponWorkspaceState {
  const tabParam = params.get('tab') as CouponTab | null;
  const viewParam = params.get('view') as CouponView | null;
  const page = parseNonNegativeInteger(params.get('page'));
  const size = parsePositiveInteger(params.get('size')) as CouponPageSize | null;

  return {
    tab: tabParam !== null && TABS.has(tabParam) ? tabParam : DEFAULT_STATE.tab,
    view: viewParam !== null && VIEWS.has(viewParam) ? viewParam : DEFAULT_STATE.view,
    search: params.get('search')?.trim() ?? DEFAULT_STATE.search,
    merchantId: parsePositiveInteger(params.get('merchantId')),
    assignedModeratorId: parsePositiveInteger(params.get('assignedModeratorId')),
    page: page ?? DEFAULT_STATE.page,
    pageSize: size !== null && PAGE_SIZES.has(size) ? size : DEFAULT_STATE.pageSize,
  };
}

export function workspaceStatuses(tab: CouponTab): CouponStatus[] {
  return [...STATUSES_BY_TAB[tab]];
}

export function nextWorkspaceSearch(
  current: URLSearchParams,
  patch: CouponWorkspacePatch,
): URLSearchParams {
  const currentState = parseWorkspaceState(current);
  const result = normalizeState({ ...currentState, ...patch });
  const resetsPage = [
    'tab',
    'search',
    'merchantId',
    'assignedModeratorId',
    'pageSize',
  ].some((key) => Object.hasOwn(patch, key));

  if (resetsPage && patch.page === undefined) {
    result.page = 0;
  }

  const params = new URLSearchParams();
  params.set('tab', result.tab);
  params.set('view', result.view);
  if (result.search) {
    params.set('search', result.search);
  }
  if (result.merchantId !== null) {
    params.set('merchantId', String(result.merchantId));
  }
  if (result.assignedModeratorId !== null) {
    params.set('assignedModeratorId', String(result.assignedModeratorId));
  }
  params.set('page', String(result.page));
  params.set('size', String(result.pageSize));
  return params;
}
