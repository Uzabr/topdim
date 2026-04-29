import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Table, Tag, Button, Modal, Input, Space, Typography, App } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { getPendingComplaints, resolveComplaint } from './api';
import type { AdminComplaint } from './api';

const { TextArea } = Input;

const STATUS_LABELS: Record<string, { label: string; color: string }> = {
  PENDING: { label: 'На рассмотрении', color: 'orange' },
  IN_REVIEW: { label: 'В работе', color: 'blue' },
  RESOLVED: { label: 'Решено', color: 'green' },
  REJECTED: { label: 'Отклонено', color: 'red' },
};

export function ComplaintsPage() {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [actionModal, setActionModal] = useState<{ type: 'resolve' | 'reject'; complaint: AdminComplaint } | null>(null);
  const [resolution, setResolution] = useState('');

  const { data, isLoading } = useQuery({
    queryKey: ['admin-complaints', page],
    queryFn: () => getPendingComplaints(page, 20),
  });

  const resolveMutation = useMutation({
    mutationFn: ({ id, decision, resolution }: { id: number; decision: 'RESOLVE' | 'REJECT'; resolution: string }) =>
      resolveComplaint(id, decision, resolution),
    onSuccess: () => {
      message.success('Обращение обработано');
      queryClient.invalidateQueries({ queryKey: ['admin-complaints'] });
      setActionModal(null);
    },
    onError: () => message.error('Ошибка обработки'),
  });

  const handleAction = () => {
    if (!actionModal || !resolution.trim()) {
      message.warning('Введите ответ');
      return;
    }
    resolveMutation.mutate({
      id: actionModal.complaint.id,
      decision: actionModal.type === 'resolve' ? 'RESOLVE' : 'REJECT',
      resolution: resolution.trim(),
    });
  };

  const columns: ColumnsType<AdminComplaint> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'User ID', dataIndex: 'userId', width: 80 },
    { title: 'Купон', dataIndex: 'couponTitle', ellipsis: true, render: (v, r) => v || `Заказ #${r.orderId}` },
    { title: 'Код', dataIndex: 'couponCode', width: 120, render: (v) => v || '—' },
    { title: 'Тема', dataIndex: 'subject', ellipsis: true },
    { title: 'Описание', dataIndex: 'description', ellipsis: true },
    {
      title: 'Статус', dataIndex: 'status', width: 130,
      render: (s: string) => {
        const info = STATUS_LABELS[s] || { label: s, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    { title: 'Дата', dataIndex: 'createdAt', width: 100, render: (d: string) => new Date(d).toLocaleDateString('ru-RU') },
    {
      title: 'Действия', width: 180,
      render: (_, record) => record.status === 'PENDING' ? (
        <Space size="small">
          <Button size="small" type="primary" onClick={() => { setResolution(''); setActionModal({ type: 'resolve', complaint: record }); }}>Решить</Button>
          <Button size="small" danger onClick={() => { setResolution(''); setActionModal({ type: 'reject', complaint: record }); }}>Отклонить</Button>
        </Space>
      ) : record.resolution ? <Typography.Text type="secondary" ellipsis>{record.resolution}</Typography.Text> : null,
    },
  ];

  return (
    <div>
      <Typography.Title level={3}>Обращения (жалобы)</Typography.Title>

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
        scroll={{ x: 900 }}
      />

      <Modal
        open={!!actionModal}
        title={actionModal?.type === 'resolve' ? 'Решить обращение' : 'Отклонить обращение'}
        onCancel={() => setActionModal(null)}
        onOk={handleAction}
        okText={actionModal?.type === 'resolve' ? 'Решить' : 'Отклонить'}
        okButtonProps={{ danger: actionModal?.type === 'reject' }}
        confirmLoading={resolveMutation.isPending}
      >
        {actionModal && (
          <div>
            <p><strong>Тема:</strong> {actionModal.complaint.subject}</p>
            <p><strong>Описание:</strong> {actionModal.complaint.description}</p>
            {actionModal.complaint.couponTitle && <p><strong>Купон:</strong> {actionModal.complaint.couponTitle}</p>}
            <TextArea
              rows={3}
              value={resolution}
              onChange={(e) => setResolution(e.target.value)}
              placeholder="Ответ / резолюция (обязательно)"
            />
          </div>
        )}
      </Modal>
    </div>
  );
}
