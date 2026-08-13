import { useMemo, useState } from 'react';
import {
  Alert,
  Badge,
  Button,
  DatePicker,
  Empty,
  Input,
  InputNumber,
  Result,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import dayjs, { type Dayjs } from 'dayjs';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import {
  fetchMerchantProfileChanges,
  fetchPendingMerchantProfileChangeCount,
  MERCHANT_PROFILE_CHANGES_QUERY_KEY,
  takeMerchantProfileChangeToWork,
} from './api';
import type {
  MerchantProfileChangeQueueFilters,
  MerchantProfileChangeStatus,
  MerchantProfileChangeSummary,
  ModerationQueueStatus,
} from './types';

const { Title, Text } = Typography;
const { RangePicker } = DatePicker;

const STATUS_INFO: Record<MerchantProfileChangeStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'default' },
  PENDING_REVIEW: { label: 'Ожидает проверки', color: 'orange' },
  IN_REVIEW: { label: 'В работе', color: 'blue' },
  REVISION_REQUESTED: { label: 'На доработке', color: 'gold' },
  APPROVED: { label: 'Одобрена', color: 'green' },
  REJECTED: { label: 'Отклонена', color: 'red' },
  WITHDRAWN: { label: 'Отозвана', color: 'default' },
  OUTDATED: { label: 'Устарела', color: 'default' },
};

const STATUS_TABS: Array<{ key: ModerationQueueStatus; label: string }> = [
  { key: 'PENDING_REVIEW', label: 'Ожидают проверки' },
  { key: 'IN_REVIEW', label: 'В работе' },
  { key: 'REVISION_REQUESTED', label: 'На доработке' },
  { key: 'APPROVED', label: 'Одобрены' },
  { key: 'REJECTED', label: 'Отклонены' },
  { key: 'WITHDRAWN', label: 'Отозваны' },
  { key: 'OUTDATED', label: 'Устарели' },
];

function errorMessage(error: unknown, fallback: string) {
  const apiError = error as { response?: { data?: { message?: string } } };
  return apiError.response?.data?.message || fallback;
}

function submittedBoundary(date: Dayjs | null, boundary: 'start' | 'end') {
  if (!date) return undefined;
  return date[boundary === 'start' ? 'startOf' : 'endOf']('day')
    .format('YYYY-MM-DDTHH:mm:ss');
}

export function MerchantProfileChangeQueuePage() {
  const [status, setStatus] = useState<ModerationQueueStatus>('PENDING_REVIEW');
  const [search, setSearch] = useState('');
  const [assigneeUserId, setAssigneeUserId] = useState<number | null>(null);
  const [period, setPeriod] = useState<[Dayjs | null, Dayjs | null] | null>(null);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const queryClient = useQueryClient();

  const filters = useMemo<MerchantProfileChangeQueueFilters>(() => ({
    status,
    search,
    ...(assigneeUserId ? { assigneeUserId } : {}),
    submittedFrom: submittedBoundary(period?.[0] ?? null, 'start'),
    submittedTo: submittedBoundary(period?.[1] ?? null, 'end'),
    page,
    size: pageSize,
  }), [status, search, assigneeUserId, period, page, pageSize]);

  const queueQuery = useQuery({
    queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'queue', filters],
    queryFn: () => fetchMerchantProfileChanges(filters),
  });

  const pendingCountQuery = useQuery({
    queryKey: [...MERCHANT_PROFILE_CHANGES_QUERY_KEY, 'pending-count'],
    queryFn: fetchPendingMerchantProfileChangeCount,
  });

  const claimMutation = useMutation({
    mutationFn: (id: number) => takeMerchantProfileChangeToWork(id),
    onSuccess: () => {
      setFeedback({ type: 'success', text: 'Заявка взята в работу' });
      queryClient.invalidateQueries({ queryKey: MERCHANT_PROFILE_CHANGES_QUERY_KEY });
    },
    onError: (error) => {
      setFeedback({
        type: 'error',
        text: errorMessage(error, 'Не удалось взять заявку в работу'),
      });
      queryClient.invalidateQueries({ queryKey: MERCHANT_PROFILE_CHANGES_QUERY_KEY });
    },
  });

  const columns: ColumnsType<MerchantProfileChangeSummary> = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    {
      title: 'Компания',
      dataIndex: 'name',
      render: (name: string, record) => (
        <div>
          <Text strong>{name}</Text>
          <div><Text type="secondary">Merchant ID: {record.merchantId}</Text></div>
        </div>
      ),
    },
    {
      title: 'Автор',
      render: (_, record) => (
        <div>
          <div>User ID: {record.authorUserId}</div>
          <Text type="secondary">{record.authorRole}</Text>
        </div>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      width: 170,
      render: (value: MerchantProfileChangeStatus) => {
        const info = STATUS_INFO[value];
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: 'Исполнитель',
      dataIndex: 'assigneeUserId',
      width: 130,
      render: (value: number | null) => value ? `User ID: ${value}` : 'Не назначен',
    },
    {
      title: 'Отправлена',
      dataIndex: 'submittedAt',
      width: 160,
      render: (value: string | null) => value ? dayjs(value).format('DD.MM.YYYY HH:mm') : '—',
    },
    {
      title: 'Действия',
      width: 170,
      render: (_, record) => record.status === 'PENDING_REVIEW' ? (
        <Button
          type="primary"
          size="small"
          loading={claimMutation.isPending && claimMutation.variables === record.id}
          disabled={claimMutation.isPending}
          onClick={() => claimMutation.mutate(record.id)}
        >
          Взять в работу
        </Button>
      ) : '—',
    },
  ];

  const onTableChange = (pagination: TablePaginationConfig) => {
    setPage((pagination.current ?? 1) - 1);
    setPageSize(pagination.pageSize ?? 20);
  };

  if (queueQuery.error) {
    return (
      <Result
        status="error"
        title="Ошибка загрузки изменений компаний"
        extra={(
          <Button loading={queueQuery.isFetching} onClick={() => queueQuery.refetch()}>
            Повторить
          </Button>
        )}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
        <Title level={4} style={{ margin: 0 }}>Изменения компаний</Title>
        <Button
          icon={<ReloadOutlined />}
          loading={queueQuery.isFetching}
          onClick={() => queryClient.invalidateQueries({ queryKey: MERCHANT_PROFILE_CHANGES_QUERY_KEY })}
        >
          Обновить
        </Button>
      </div>

      <Tabs
        activeKey={status}
        onChange={(value) => { setStatus(value as typeof status); setPage(0); setFeedback(null); }}
        items={STATUS_TABS.map((item) => ({
          key: item.key,
          label: item.key === 'PENDING_REVIEW' ? (
            <Space size={6}>
              {item.label}
              <Badge count={pendingCountQuery.data ?? 0} showZero />
            </Space>
          ) : item.label,
        }))}
      />

      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="Компания, автор или ID"
          value={search}
          onChange={(event) => { setSearch(event.target.value); setPage(0); }}
          style={{ width: 260, maxWidth: '100%' }}
        />
        <InputNumber
          aria-label="ID исполнителя"
          placeholder="ID исполнителя"
          min={1}
          precision={0}
          value={assigneeUserId}
          onChange={(value) => { setAssigneeUserId(value); setPage(0); }}
          style={{ width: 170, maxWidth: '100%' }}
        />
        <RangePicker
          aria-label="Период отправки"
          allowEmpty={[true, true]}
          format="DD.MM.YYYY"
          value={period}
          onChange={(dates) => { setPeriod(dates); setPage(0); }}
        />
      </Space>

      {feedback && (
        <Alert
          type={feedback.type}
          title={feedback.text}
          showIcon
          closable
          onClose={() => setFeedback(null)}
          style={{ marginBottom: 16 }}
        />
      )}

      <Table
        columns={columns}
        dataSource={queueQuery.data?.content}
        loading={queueQuery.isLoading}
        rowKey="id"
        onChange={onTableChange}
        locale={{
          emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Нет заявок" />,
        }}
        pagination={{
          current: page + 1,
          pageSize,
          total: queueQuery.data?.totalElements,
          showSizeChanger: true,
          pageSizeOptions: [20, 50, 100],
          showTotal: (total) => `Всего заявок: ${total}`,
        }}
        scroll={{ x: 1050 }}
      />
    </div>
  );
}
