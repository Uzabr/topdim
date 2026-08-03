import { useCallback, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Typography } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../../api/client';
import type { ApiResponse, PageResponse } from '../../../types';
import { CouponStatusTabs } from './CouponStatusTabs';
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

const { Title, Text } = Typography;

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
    queryKey: [...ADMIN_COUPONS_WORKSPACE_QUERY_KEY, state],
    queryFn: async () => {
      const data = await fetchAdminCoupons(state);
      setLastSuccessfulResult({ data, updatedAt: Date.now() });
      return data;
    },
    placeholderData: keepPreviousData,
    enabled: state.view === 'table',
  });

  const visibleCouponData = couponsQuery.data
    ?? (couponsQuery.error === null ? undefined : lastSuccessfulResult?.data);

  const updateState = useCallback((patch: CouponWorkspacePatch) => {
    setSearchParams(nextWorkspaceSearch(searchParams, patch));
  }, [searchParams, setSearchParams]);
  const handleSearchChange = useCallback((search: string) => {
    updateState({ search });
  }, [updateState]);

  return (
    <div>
      <Title level={2} style={{ marginTop: 0 }}>Купоны</Title>

      <CouponWorkspaceToolbar
        search={state.search}
        merchantId={state.merchantId}
        assignedModeratorId={state.assignedModeratorId}
        view={state.view}
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

      <section aria-label={state.view === 'table' ? 'Таблица купонов' : 'Kanban купонов'}>
        {state.view === 'table' ? (
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
          <Text type="secondary">Kanban-представление купонов</Text>
        )}
      </section>
    </div>
  );
}
