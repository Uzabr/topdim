import { useCallback, useEffect, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Typography } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../../api/client';
import type { ApiResponse, PageResponse } from '../../../types';
import { CouponStatusTabs } from './CouponStatusTabs';
import { CouponKanbanView } from './CouponKanbanView';
import { CouponTableView } from './CouponTableView';
import {
  CouponWorkspaceToolbar,
  type CouponFilterOption,
} from './CouponWorkspaceToolbar';
import { nextWorkspaceSearch, parseWorkspaceState } from './state';
import type { AdminCouponRow, CouponWorkspacePatch } from './types';
import {
  ADMIN_COUPONS_WORKSPACE_QUERY_KEY,
  fetchAdminCoupons,
} from './api';

const { Title } = Typography;

async function fetchFilterOptions(endpoint: string): Promise<CouponFilterOption[]> {
  const response = await api.get<ApiResponse<CouponFilterOption[]>>(endpoint);
  return response.data.data;
}

export function CouponWorkspacePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const state = parseWorkspaceState(searchParams);
  const [lastSuccessfulResult, setLastSuccessfulResult] = useState<{
    data: PageResponse<AdminCouponRow>;
    updatedAt: number;
  } | null>(null);
  const kanbanAllowed = state.tab !== 'published' && state.tab !== 'archived';
  const effectiveView = state.view === 'kanban' && !kanbanAllowed
    ? 'table'
    : state.view;

  const merchantsQuery = useQuery({
    queryKey: ['admin-coupons', 'merchant-options'],
    queryFn: () => fetchFilterOptions('/api/v1/admin/merchants'),
    staleTime: 5 * 60_000,
  });
  const assigneesQuery = useQuery({
    queryKey: ['admin-coupons', 'assignee-options'],
    queryFn: () => fetchFilterOptions('/api/v1/admin/coupons/assignees'),
    staleTime: 5 * 60_000,
  });
  const couponsQuery = useQuery({
    queryKey: [
      ...ADMIN_COUPONS_WORKSPACE_QUERY_KEY,
      state.tab,
      state.search,
      state.merchantId,
      state.assignedModeratorId,
      state.page,
      state.pageSize,
    ],
    queryFn: async () => {
      const data = await fetchAdminCoupons(state);
      setLastSuccessfulResult({ data, updatedAt: Date.now() });
      return data;
    },
    placeholderData: keepPreviousData,
    enabled: effectiveView === 'table',
  });

  const visibleCouponData = couponsQuery.data
    ?? (couponsQuery.error === null ? undefined : lastSuccessfulResult?.data);

  const updateState = useCallback((patch: CouponWorkspacePatch) => {
    setSearchParams(nextWorkspaceSearch(searchParams, patch));
  }, [searchParams, setSearchParams]);
  const handleSearchChange = useCallback((search: string) => {
    updateState({ search });
  }, [updateState]);

  useEffect(() => {
    if (state.view === 'kanban' && !kanbanAllowed) {
      setSearchParams(nextWorkspaceSearch(searchParams, { view: 'table' }), {
        replace: true,
      });
    }
  }, [kanbanAllowed, searchParams, setSearchParams, state.view]);

  return (
    <div>
      <Title level={2} style={{ marginTop: 0 }}>Купоны</Title>

      <CouponWorkspaceToolbar
        search={state.search}
        merchantId={state.merchantId}
        assignedModeratorId={state.assignedModeratorId}
        view={effectiveView}
        kanbanEnabled={kanbanAllowed}
        merchantOptions={merchantsQuery.data ?? []}
        assigneeOptions={assigneesQuery.data ?? []}
        onSearchChange={handleSearchChange}
        onMerchantChange={(merchantId) => updateState({ merchantId })}
        onAssigneeChange={(assignedModeratorId) => updateState({ assignedModeratorId })}
        onViewChange={(view) => updateState({ view })}
        onCreate={() => navigate('/coupons/new')}
      />

      <CouponStatusTabs
        activeTab={state.tab}
        onChange={(tab) => updateState({ tab })}
      />

      <section aria-label={effectiveView === 'table' ? 'Таблица купонов' : 'Kanban купонов'}>
        {effectiveView === 'table' ? (
          <CouponTableView
            activeTab={state.tab}
            pageSize={state.pageSize}
            data={visibleCouponData}
            isLoading={couponsQuery.isPending}
            error={couponsQuery.error}
            isStaleData={couponsQuery.error !== null && visibleCouponData !== undefined}
            lastSuccessfulAt={lastSuccessfulResult?.updatedAt ?? null}
            onRetry={() => { void couponsQuery.refetch(); }}
            onPageChange={(page, pageSize) => updateState({ page, pageSize })}
          />
        ) : (
          <CouponKanbanView state={state} />
        )}
      </section>
    </div>
  );
}
