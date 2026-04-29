import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Table, Tag, Button, Modal, Input, Space, Select, Typography, App } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getAdminRefunds, approveRefund, rejectRefund, completeRefund } from './api';
import type { AdminRefund } from './api';

const { TextArea } = Input;
const { Text } = Typography;

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  PENDING: { label: 'На рассмотрении', color: 'orange' },
  APPROVED_PROCESSING: { label: 'Одобрен', color: 'blue' },
  REFUNDED: { label: 'Завершён', color: 'green' },
  REJECTED: { label: 'Отклонён', color: 'red' },
};

export function RefundsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);
  const [page, setPage] = useState(0);
  const [actionModal, setActionModal] = useState<{ type: 'approve' | 'reject' | 'complete'; refund: AdminRefund } | null>(null);
  const [comment, setComment] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['admin-refunds', statusFilter, page],
    queryFn: () => getAdminRefunds(statusFilter, page, 20),
  });

  const approveMutation = useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => approveRefund(id, comment),
    onSuccess: () => { message.success('Возврат одобрен'); queryClient.invalidateQueries({ queryKey: ['admin-refunds'] }); setActionModal(null); },
    onError: () => message.error('Ошибка одобрения'),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => rejectRefund(id, comment),
    onSuccess: () => { message.success('Возврат отклонён'); queryClient.invalidateQueries({ queryKey: ['admin-refunds'] }); setActionModal(null); },
    onError: () => message.error('Ошибка отклонения'),
  });

  const completeMutation = useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) => completeRefund(id, comment),
    onSuccess: () => { message.success('Возврат завершён'); queryClient.invalidateQueries({ queryKey: ['admin-refunds'] }); setActionModal(null); },
    onError: () => message.error('Ошибка завершения'),
  });

  const handleAction = () => {
    if (!actionModal) return;
    const payload = { id: actionModal.refund.id, comment: comment || undefined };
    if (actionModal.type === 'approve') approveMutation.mutate(payload);
    if (actionModal.type === 'reject') rejectMutation.mutate(payload);
    if (actionModal.type === 'complete') completeMutation.mutate(payload);
  };

  const columns: ColumnsType<AdminRefund> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'Купон', dataIndex: 'couponTitle', ellipsis: true },
    { title: 'Код', dataIndex: 'couponCode', width: 120 },
    { title: 'User ID', dataIndex: 'userId', width: 80 },
    { title: 'Мерчант', dataIndex: 'merchantName', ellipsis: true },
    { title: 'Сумма', dataIndex: 'refundAmount', width: 100, render: (v) => v != null ? `${v.toLocaleString()} сум` : '—' },
    { title: 'Причина', dataIndex: 'reason', ellipsis: true },
    {
      title: 'Статус', dataIndex: 'status', width: 140,
      render: (s: string) => {
        const info = STATUS_LABELS[s] || { label: s, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    { title: 'Дата', dataIndex: 'createdAt', width: 100, render: (d: string) => new Date(d).toLocaleDateString('ru-RU') },
    {
      title: 'Действия', width: 200,
      render: (_, record) => (
        <Space size="small">
          {record.status === 'PENDING' && (
            <>
              <Button size="small" type="primary" onClick={() => { setComment(''); setActionModal({ type: 'approve', refund: record }); }}>Одобрить</Button>
              <Button size="small" danger onClick={() => { setComment(''); setActionModal({ type: 'reject', refund: record }); }}>Отклонить</Button>
            </>
          )}
          {record.status === 'APPROVED_PROCESSING' && (
            <Button size="small" type="primary" onClick={() => { setComment(''); setActionModal({ type: 'complete', refund: record }); }}>Завершить</Button>
          )}
        </Space>
      ),
    },
  ];

  const modalTitle = actionModal?.type === 'approve' ? 'Одобрить возврат' : actionModal?.type === 'reject' ? 'Отклонить возврат' : 'Завершить возврат';

  return (
    <div>
      <Typography.Title level={3}>Возвраты</Typography.Title>

      <Space style={{ marginBottom: 16 }}>
        <Text>Фильтр:</Text>
        <Select
          style={{ width: 200 }}
          placeholder="Все статусы"
          allowClear
          value={statusFilter}
          onChange={(v) => { setStatusFilter(v); setPage(0); }}
          options={[
            { value: 'PENDING', label: 'На рассмотрении' },
            { value: 'APPROVED_PROCESSING', label: 'Одобрены' },
            { value: 'REFUNDED', label: 'Завершены' },
            { value: 'REJECTED', label: 'Отклонены' },
          ]}
        />
      </Space>

      <Table
        rowKey="id"
        columns={columns}
        dataSource={data?.content || []}
        loading={isLoading}
        pagination={{
          total: data?.totalElements || 0,
          pageSize: 20,
          current: page + 1,
          onChange: (p) => setPage(p - 1),
          showSizeChanger: false,
        }}
        scroll={{ x: 1000 }}
      />

      <Modal
        open={!!actionModal}
        title={modalTitle}
        onCancel={() => setActionModal(null)}
        onOk={handleAction}
        okText={modalTitle}
        okButtonProps={{ danger: actionModal?.type === 'reject' }}
        confirmLoading={approveMutation.isPending || rejectMutation.isPending || completeMutation.isPending}
      >
        {actionModal && (
          <div>
            <p><strong>Купон:</strong> {actionModal.refund.couponTitle} ({actionModal.refund.couponCode})</p>
            <p><strong>Причина:</strong> {actionModal.refund.reason}</p>
            {actionModal.refund.refundAmount != null && <p><strong>Сумма:</strong> {actionModal.refund.refundAmount.toLocaleString()} сум</p>}
            <TextArea
              rows={3}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="Комментарий администратора (необязательно)"
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
