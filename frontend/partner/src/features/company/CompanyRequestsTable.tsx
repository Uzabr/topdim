import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  App as AntApp,
  Button,
  Card,
  Input,
  Modal,
  Result,
  Space,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { TableProps } from 'antd';
import { CopyOutlined, EditOutlined, EyeOutlined, StopOutlined } from '@ant-design/icons';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { companyApi } from './api';
import type { CompanyChangeSummary, CompanyProfileChangeStatus } from './types';

const { Text, Title } = Typography;

const STATUS_LABELS: Record<CompanyProfileChangeStatus, { label: string; color: string }> = {
  DRAFT: { label: 'Черновик', color: 'blue' },
  PENDING_REVIEW: { label: 'Ожидает проверки', color: 'gold' },
  IN_REVIEW: { label: 'На проверке', color: 'processing' },
  REVISION_REQUESTED: { label: 'Нужны исправления', color: 'orange' },
  APPROVED: { label: 'Одобрена', color: 'green' },
  REJECTED: { label: 'Отклонена', color: 'red' },
  WITHDRAWN: { label: 'Отозвана', color: 'default' },
  OUTDATED: { label: 'Устарела', color: 'default' },
};
const EDITABLE = new Set<CompanyProfileChangeStatus>(['DRAFT', 'REVISION_REQUESTED']);
const WITHDRAWABLE = new Set<CompanyProfileChangeStatus>([
  'PENDING_REVIEW',
  'IN_REVIEW',
  'REVISION_REQUESTED',
]);
const COPYABLE = new Set<CompanyProfileChangeStatus>([
  'APPROVED',
  'REJECTED',
  'WITHDRAWN',
  'OUTDATED',
]);

function formatDate(value: string | null): string {
  return value ? new Date(value).toLocaleString('ru-RU') : '—';
}

export default function CompanyRequestsTable() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message } = AntApp.useApp();
  const [page, setPage] = useState(0);
  const [withdrawal, setWithdrawal] = useState<CompanyChangeSummary | null>(null);
  const [withdrawalReason, setWithdrawalReason] = useState('');
  const filters = { page, size: 10 };
  const requestsQuery = useQuery({
    queryKey: ['company-change-requests', filters],
    queryFn: () => companyApi.listRequests(filters),
  });

  const refreshRequests = async () => {
    await queryClient.invalidateQueries({ queryKey: ['company-change-requests'] });
    await queryClient.invalidateQueries({ queryKey: ['company-profile'] });
  };
  const withdrawMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      companyApi.withdraw(id, reason),
    onSuccess: async () => {
      setWithdrawal(null);
      setWithdrawalReason('');
      await refreshRequests();
      message.success('Заявка отозвана');
    },
    onError: () => message.error('Не удалось отозвать заявку'),
  });
  const copyMutation = useMutation({
    mutationFn: companyApi.copy,
    onSuccess: async (draft) => {
      await refreshRequests();
      navigate(`/company/requests/${draft.id}`);
    },
    onError: () => message.error('Не удалось создать копию заявки'),
  });

  const columns: TableProps<CompanyChangeSummary>['columns'] = [
    {
      title: 'Заявка',
      key: 'request',
      render: (_, request) => (
        <Space orientation="vertical" size={0}>
          <Text strong>#{request.id}</Text>
          <Text>{request.name}</Text>
          <Text type="secondary">Версия {request.baseProfileVersion}</Text>
        </Space>
      ),
    },
    {
      title: 'Статус',
      key: 'status',
      render: (_, request) => (
        <Space orientation="vertical" size={4}>
          <Tag color={STATUS_LABELS[request.status].color}>
            {STATUS_LABELS[request.status].label}
          </Tag>
          {request.moderationComment ? (
            <Text
              type={request.status === 'REJECTED'
                ? 'danger'
                : request.status === 'REVISION_REQUESTED' ? 'warning' : 'secondary'}
            >
              {request.moderationComment}
            </Text>
          ) : null}
        </Space>
      ),
    },
    {
      title: 'Автор и исполнитель',
      key: 'people',
      render: (_, request) => (
        <Space orientation="vertical" size={0}>
          <Text>Автор: {request.authorRole} #{request.authorStaffId ?? request.authorUserId}</Text>
          <Text type="secondary">
            {request.assigneeUserId ? `Исполнитель #${request.assigneeUserId}` : 'Не назначен'}
          </Text>
        </Space>
      ),
    },
    {
      title: 'Обновлена',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      render: formatDate,
    },
    {
      title: 'Действия',
      key: 'actions',
      render: (_, request) => (
        <Space wrap>
          {EDITABLE.has(request.status) ? (
            <Button
              type="link"
              icon={<EditOutlined />}
              onClick={() => navigate(`/company/requests/${request.id}`)}
            >
              Изменить
            </Button>
          ) : null}
          {!EDITABLE.has(request.status) ? (
            <Button
              type="link"
              icon={<EyeOutlined />}
              onClick={() => navigate(`/company/requests/${request.id}`)}
            >
              Просмотреть
            </Button>
          ) : null}
          {WITHDRAWABLE.has(request.status) ? (
            <Button
              type="link"
              danger
              icon={<StopOutlined />}
              onClick={() => {
                setWithdrawal(request);
                setWithdrawalReason('');
              }}
            >
              Отозвать
            </Button>
          ) : null}
          {COPYABLE.has(request.status) ? (
            <Button
              type="link"
              icon={<CopyOutlined />}
              loading={copyMutation.isPending}
              onClick={() => copyMutation.mutate(request.id)}
            >
              Создать копию
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  if (requestsQuery.error) {
    return <Result status="error" title="Не удалось загрузить заявки" />;
  }

  const data = requestsQuery.data;
  return (
    <Card style={{ marginTop: 16 }}>
      <Title level={3}>Мои заявки</Title>
      <Table
        rowKey="id"
        loading={requestsQuery.isLoading}
        dataSource={data?.content ?? []}
        columns={columns}
        scroll={{ x: 900 }}
        locale={{ emptyText: 'Заявок пока нет' }}
        pagination={{
          current: (data?.number ?? page) + 1,
          pageSize: data?.size ?? 10,
          total: data?.totalElements ?? 0,
          showSizeChanger: false,
          onChange: (nextPage) => setPage(nextPage - 1),
        }}
      />

      <Modal
        title={`Отозвать заявку #${withdrawal?.id ?? ''}`}
        open={withdrawal !== null}
        onCancel={() => {
          setWithdrawal(null);
          setWithdrawalReason('');
        }}
        footer={[
          <Button key="cancel" onClick={() => setWithdrawal(null)}>Отмена</Button>,
          <Button
            key="confirm"
            type="primary"
            danger
            disabled={!withdrawalReason.trim()}
            loading={withdrawMutation.isPending}
            onClick={() => {
              if (withdrawal && withdrawalReason.trim()) {
                withdrawMutation.mutate({
                  id: withdrawal.id,
                  reason: withdrawalReason.trim(),
                });
              }
            }}
          >
            Подтвердить отзыв
          </Button>,
        ]}
      >
        <label htmlFor="company-withdrawal-reason">Причина отзыва</label>
        <Input.TextArea
          id="company-withdrawal-reason"
          value={withdrawalReason}
          maxLength={2000}
          showCount
          rows={4}
          onChange={(event) => setWithdrawalReason(event.target.value)}
        />
      </Modal>
    </Card>
  );
}
