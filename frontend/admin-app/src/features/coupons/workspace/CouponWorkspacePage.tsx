import { useCallback } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Typography } from 'antd';
import { useNavigate, useSearchParams } from 'react-router-dom';
import api from '../../../api/client';
import type { ApiResponse } from '../../../types';
import { CouponStatusTabs } from './CouponStatusTabs';
import {
  CouponWorkspaceToolbar,
  type CouponFilterOption,
} from './CouponWorkspaceToolbar';
import { nextWorkspaceSearch, parseWorkspaceState } from './state';
import type { CouponWorkspacePatch } from './types';

const { Title, Text } = Typography;

async function fetchFilterOptions(endpoint: string): Promise<CouponFilterOption[]> {
  const response = await api.get<ApiResponse<CouponFilterOption[]>>(endpoint);
  return response.data.data;
}

export function CouponWorkspacePage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const state = parseWorkspaceState(searchParams);

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
        pageSize={state.pageSize}
        view={state.view}
        merchantOptions={merchantsQuery.data ?? []}
        assigneeOptions={assigneesQuery.data ?? []}
        onSearchChange={handleSearchChange}
        onMerchantChange={(merchantId) => updateState({ merchantId })}
        onAssigneeChange={(assignedModeratorId) => updateState({ assignedModeratorId })}
        onPageSizeChange={(pageSize) => updateState({ pageSize })}
        onViewChange={(view) => updateState({ view })}
        onCreate={() => navigate('/coupons/new')}
      />

      <CouponStatusTabs
        activeTab={state.tab}
        onChange={(tab) => updateState({ tab })}
      />

      <section aria-label={state.view === 'table' ? 'Таблица купонов' : 'Kanban купонов'}>
        <Text type="secondary">
          {state.view === 'table'
            ? 'Табличное представление купонов'
            : 'Kanban-представление купонов'}
        </Text>
      </section>
    </div>
  );
}
