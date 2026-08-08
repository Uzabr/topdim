import { useMemo, useState } from 'react';
import {
  Button, DatePicker, Empty, Input, Result, Select, Space, Table, Tag, Typography,
} from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import type { SorterResult } from 'antd/es/table/interface';

const { Title } = Typography;
const { RangePicker } = DatePicker;

interface AuditLog {
  id: number;
  userId: number;
  userEmail: string | null;
  userName: string | null;
  userRole: string | null;
  action: string;
  entityName: string | null;
  entityId: number | null;
  details: string | null;
  createdAt: string;
}

const ACTION_LABELS: Record<string, string> = {
  CREATE_ADMIN: 'Создание сотрудника',
  DELETE_USER: 'Удаление пользователя',
  CHANGE_ROLE: 'Смена роли',
  BLOCK_USER: 'Блокировка',
  UNBLOCK_USER: 'Разблокировка',
  APPROVE_PARTNER: 'Одобрение заявки партнёра',
  REJECT_PARTNER: 'Отклонение заявки партнёра',
};

const ENTITY_LABELS: Record<string, string> = {
  USER: 'Пользователи',
  users: 'Пользователи',
  staff: 'Сотрудники',
  'partner-applications': 'Заявки партнёров',
};

const ROLE_LABELS: Record<string, { label: string; color: string }> = {
  MODERATOR: { label: 'Модератор', color: 'orange' },
  ADMIN: { label: 'Админ', color: 'red' },
  SUPER_ADMIN: { label: 'Супер-админ', color: 'volcano' },
  USER: { label: 'Пользователь', color: 'blue' },
  PARTNER: { label: 'Партнёр', color: 'purple' },
};

type SortState = { field: string; order: 'asc' | 'desc' };

export const AuditLogPage = () => {
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState<string | undefined>();
  const [entityFilter, setEntityFilter] = useState<string | undefined>();
  const [period, setPeriod] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [sort, setSort] = useState<SortState>({ field: 'createdAt', order: 'desc' });

  const queryParams = useMemo(() => ({
    page,
    size: pageSize,
    search: search || undefined,
    action: actionFilter,
    entityName: entityFilter,
    from: period?.[0]?.format('YYYY-MM-DD'),
    to: period?.[1]?.format('YYYY-MM-DD'),
    sort: `${sort.field},${sort.order}`,
  }), [page, pageSize, search, actionFilter, entityFilter, period, sort]);

  const { data, error, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['audit-logs', queryParams],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<AuditLog>>>(
        '/api/v1/admin/audit-logs',
        { params: queryParams },
      );
      return res.data.data;
    },
  });

  const columns: ColumnsType<AuditLog> = [
    {
      title: 'Пользователь',
      key: 'user',
      sorter: true,
      sortOrder: sort.field === 'userName' ? (sort.order === 'asc' ? 'ascend' : 'descend') : undefined,
      render: (_, record) => (
        <div>
          <div style={{ fontWeight: 500 }}>
            {record.userName || (record.userEmail ? record.userEmail.split('@')[0] : `ID ${record.userId}`)}
          </div>
          <div style={{ fontSize: 12, color: '#888' }}>
            {record.userEmail || `ID: ${record.userId}`}
          </div>
        </div>
      ),
    },
    {
      title: 'Роль',
      dataIndex: 'userRole',
      width: 140,
      sorter: true,
      sortOrder: sort.field === 'userRole' ? (sort.order === 'asc' ? 'ascend' : 'descend') : undefined,
      render: (role: string | null) => {
        if (!role) return '—';
        const info = ROLE_LABELS[role] || { label: role, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: 'Раздел',
      dataIndex: 'entityName',
      width: 180,
      sorter: true,
      sortOrder: sort.field === 'entityName' ? (sort.order === 'asc' ? 'ascend' : 'descend') : undefined,
      render: (entity: string | null, record) => {
        const label = entity ? (ENTITY_LABELS[entity] ?? entity) : '—';
        return record.entityId != null ? `${label} #${record.entityId}` : label;
      },
    },
    {
      title: 'Действие',
      dataIndex: 'action',
      width: 200,
      sorter: true,
      sortOrder: sort.field === 'action' ? (sort.order === 'asc' ? 'ascend' : 'descend') : undefined,
      render: (val: string, record) => (
        <div>
          <Tag color="blue">{ACTION_LABELS[val] ?? val}</Tag>
          {record.details && (
            <div style={{ fontSize: 12, color: '#888', marginTop: 4, maxWidth: 280 }}>
              {record.details}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Дата и время',
      dataIndex: 'createdAt',
      width: 160,
      sorter: true,
      sortOrder: sort.field === 'createdAt' ? (sort.order === 'asc' ? 'ascend' : 'descend') : undefined,
      defaultSortOrder: 'descend',
      render: (date: string) => dayjs(date).format('DD.MM.YYYY HH:mm:ss'),
    },
  ];

  const onTableChange = (
    pagination: TablePaginationConfig,
    _filters: unknown,
    sorter: SorterResult<AuditLog> | SorterResult<AuditLog>[],
  ) => {
    const nextPage = (pagination.current ?? 1) - 1;
    const nextSize = pagination.pageSize ?? 20;
    setPage(nextPage);
    setPageSize(nextSize);

    const single = Array.isArray(sorter) ? sorter[0] : sorter;
    if (single?.field && single.order) {
      const field = String(single.field === 'user' ? 'userName' : single.field);
      setSort({
        field,
        order: single.order === 'ascend' ? 'asc' : 'desc',
      });
    } else if (!single?.order) {
      setSort({ field: 'createdAt', order: 'desc' });
    }
  };

  if (error) {
    return (
      <Result
        status="error"
        title="Ошибка загрузки журнала действий"
        extra={<Button loading={isFetching} onClick={() => refetch()}>Повторить</Button>}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16, gap: 12, flexWrap: 'wrap' }}>
        <Title level={4} style={{ margin: 0 }}>Журнал действий</Title>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => refetch()}
          loading={isFetching}
        >
          Обновить
        </Button>
      </div>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          allowClear
          placeholder="Поиск по имени, email, деталям..."
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          style={{ width: 280, maxWidth: '100%' }}
        />
        <Select
          allowClear
          placeholder="Действие"
          value={actionFilter}
          onChange={(v) => { setActionFilter(v); setPage(0); }}
          style={{ width: 220, maxWidth: '100%' }}
          options={Object.entries(ACTION_LABELS).map(([value, label]) => ({ value, label }))}
        />
        <Select
          allowClear
          placeholder="Раздел"
          value={entityFilter}
          onChange={(v) => { setEntityFilter(v); setPage(0); }}
          style={{ width: 200, maxWidth: '100%' }}
          options={[
            { value: 'users', label: 'Пользователи' },
            { value: 'staff', label: 'Сотрудники' },
            { value: 'partner-applications', label: 'Заявки партнёров' },
            { value: 'USER', label: 'Пользователи (старые записи)' },
          ]}
        />
        <RangePicker
          allowEmpty={[true, true]}
          format="DD.MM.YYYY"
          value={period}
          onChange={(dates) => {
            setPeriod(dates);
            setPage(0);
          }}
        />
      </Space>

      <Table
        columns={columns}
        dataSource={data?.content}
        loading={isLoading}
        rowKey="id"
        onChange={onTableChange}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Нет записей в журнале" /> }}
        pagination={{
          current: page + 1,
          pageSize,
          total: data?.totalElements,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (total) => `Всего записей: ${total}`,
        }}
        scroll={{ x: 960 }}
      />
    </div>
  );
};
