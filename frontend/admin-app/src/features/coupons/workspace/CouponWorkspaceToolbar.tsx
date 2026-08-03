import { useEffect, useState } from 'react';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Input, Space } from 'antd';
import type { CouponView } from './types';

export interface CouponFilterOption {
  id: number;
  name: string;
}

interface CouponWorkspaceToolbarProps {
  search: string;
  merchantId: number | null;
  assignedModeratorId: number | null;
  view: CouponView;
  merchantOptions: CouponFilterOption[];
  assigneeOptions: CouponFilterOption[];
  onSearchChange: (search: string) => void;
  onMerchantChange: (merchantId: number | null) => void;
  onAssigneeChange: (assignedModeratorId: number | null) => void;
  onViewChange: (view: CouponView) => void;
  onCreate: () => void;
}

const selectStyle = {
  minHeight: 32,
  padding: '4px 11px',
  border: '1px solid #d9d9d9',
  borderRadius: 6,
  background: '#fff',
} as const;

export function CouponWorkspaceToolbar({
  search,
  merchantId,
  assignedModeratorId,
  view,
  merchantOptions,
  assigneeOptions,
  onSearchChange,
  onMerchantChange,
  onAssigneeChange,
  onViewChange,
  onCreate,
}: CouponWorkspaceToolbarProps) {
  const [draftSearch, setDraftSearch] = useState(search);

  useEffect(() => {
    setDraftSearch(search);
  }, [search]);

  useEffect(() => {
    const normalizedSearch = draftSearch.trim();
    if (normalizedSearch === search) {
      return undefined;
    }

    const timeoutId = window.setTimeout(() => {
      onSearchChange(normalizedSearch);
    }, 300);

    return () => window.clearTimeout(timeoutId);
  }, [draftSearch, onSearchChange, search]);

  return (
    <Space wrap size={[12, 12]} style={{ marginBottom: 16, width: '100%' }}>
      <Input
        type="search"
        aria-label="Поиск купонов"
        placeholder="Поиск по купону или партнёру"
        prefix={<SearchOutlined />}
        value={draftSearch}
        onChange={(event) => setDraftSearch(event.target.value)}
        allowClear
        style={{ width: 300 }}
      />

      <select
        aria-label="Партнёр"
        value={merchantId ?? ''}
        onChange={(event) => onMerchantChange(
          event.target.value ? Number(event.target.value) : null,
        )}
        style={{ ...selectStyle, minWidth: 180 }}
      >
        <option value="">Все партнёры</option>
        {merchantOptions.map((merchant) => (
          <option key={merchant.id} value={merchant.id}>{merchant.name}</option>
        ))}
      </select>

      <select
        aria-label="Ответственный"
        value={assignedModeratorId ?? ''}
        onChange={(event) => onAssigneeChange(
          event.target.value ? Number(event.target.value) : null,
        )}
        style={{ ...selectStyle, minWidth: 190 }}
      >
        <option value="">Все ответственные</option>
        {assigneeOptions.map((assignee) => (
          <option key={assignee.id} value={assignee.id}>{assignee.name}</option>
        ))}
      </select>

      <Space.Compact>
        <Button
          type={view === 'table' ? 'primary' : 'default'}
          aria-pressed={view === 'table'}
          onClick={() => onViewChange('table')}
        >
          Таблица
        </Button>
        <Button
          type={view === 'kanban' ? 'primary' : 'default'}
          aria-pressed={view === 'kanban'}
          onClick={() => onViewChange('kanban')}
        >
          Kanban
        </Button>
      </Space.Compact>

      <Button
        type="primary"
        icon={<PlusOutlined />}
        aria-label="Создать купон"
        onClick={onCreate}
      >
        Создать купон
      </Button>
    </Space>
  );
}
