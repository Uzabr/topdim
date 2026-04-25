import { useState } from 'react';
import { Table, Tag, Button, Space, Typography, Popconfirm, Modal, Input, message } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined, StopOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';

const { Title } = Typography;

interface CouponRow {
  id: number;
  title: string;
  oldPrice: number | null;
  fromPrice: number;
  discountPercent: number;
  status: string;
  createdAt: string;
  archiveReason?: string | null;
  archivedAt?: string | null;
}

export const CouponsListPage = () => {
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-coupons', page],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<CouponRow>>>(
        '/api/v1/admin/coupons',
        { params: { page, size: 20 } }
      );
      return res.data.data;
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/api/v1/admin/coupons/${id}`),
    onSuccess: () => {
      message.success('Купон удалён');
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
    },
  });

  const statusColors: Record<string, string> = {
    LEAD: 'blue',
    DRAFT: 'default',
    WAITING_FOR_MERCHANT: 'purple',
    REVISION_REQUESTED: 'orange',
    ACTIVE: 'green',
    SOLD_OUT: 'cyan',
    ARCHIVED: 'default',
  };

  const [archiveModalOpen, setArchiveModalOpen] = useState(false);
  const [archiveTargetId, setArchiveTargetId] = useState<number | null>(null);
  const [archiveReason, setArchiveReason] = useState('');

  const archiveMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      api.post(`/api/v1/admin/coupons/${id}/archive`, { reason }),
    onSuccess: () => {
      message.success('Купон снят с публикации');
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
      setArchiveModalOpen(false);
      setArchiveReason('');
      setArchiveTargetId(null);
    },
  });

  const columns: ColumnsType<CouponRow> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'Название', dataIndex: 'title' },
    { title: 'Старая цена', dataIndex: 'oldPrice', render: (val) => val ? `${val} сум` : '-' },
    { title: 'Новая цена', dataIndex: 'fromPrice', render: (val) => `${val} сум` },
    { title: 'Скидка', dataIndex: 'discountPercent', render: (val) => `${val}%`, width: 80 },
    { 
      title: 'Статус', 
      dataIndex: 'status',
      render: (status: string) => <Tag color={statusColors[status] || 'default'}>{status}</Tag>,
    },
    { 
      title: 'Создан', 
      dataIndex: 'createdAt',
      render: (date: string) => dayjs(date).format('DD.MM.YY HH:mm'),
    },
    {
      title: 'Действия',
      width: 140,
      render: (_, record) => {
        const isEditable = record.status === 'LEAD' || record.status === 'DRAFT' || record.status === 'REVISION_REQUESTED';
        const isDeletable = record.status === 'LEAD' || record.status === 'DRAFT' || record.status === 'REVISION_REQUESTED';
        const isArchivable = record.status === 'ACTIVE' || record.status === 'SOLD_OUT';
        return (
          <Space>
            {isEditable ? (
              <Button type="text" icon={<EditOutlined />} onClick={() => navigate(`/moderation/coupons/edit/${record.id}`)} />
            ) : (
              <Button type="text" icon={<EditOutlined />} disabled title="Редактирование заблокировано" />
            )}
            {isDeletable ? (
              <Popconfirm
                title="Удалить купон?"
                description="Это действие необратимо."
                onConfirm={() => deleteMutation.mutate(record.id)}
                okText="Да"
                cancelText="Нет"
              >
                <Button type="text" danger icon={<DeleteOutlined />} loading={deleteMutation.isPending} />
              </Popconfirm>
            ) : (
              <Button type="text" danger icon={<DeleteOutlined />} disabled title="Удаление заблокировано" />
            )}
            {isArchivable && (
              <Button
                type="text"
                icon={<StopOutlined />}
                title="Снять с публикации"
                onClick={() => {
                  setArchiveTargetId(record.id);
                  setArchiveModalOpen(true);
                }}
              />
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Все купоны</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/moderation/coupons/create')}>
          Создать купон
        </Button>
      </div>

      <Table
        columns={columns}
        dataSource={data?.content}
        loading={isLoading}
        rowKey="id"
        pagination={{
          current: page + 1,
          pageSize: 20,
          total: data?.totalElements,
          onChange: (p) => setPage(p - 1),
          showTotal: (total) => `Всего: ${total}`,
        }}
      />

      <Modal
        title="Снять купон с публикации"
        open={archiveModalOpen}
        onCancel={() => { setArchiveModalOpen(false); setArchiveReason(''); setArchiveTargetId(null); }}
        onOk={() => {
          if (!archiveTargetId || !archiveReason.trim()) return;
          archiveMutation.mutate({ id: archiveTargetId, reason: archiveReason.trim() });
        }}
        okText="Архивировать"
        cancelText="Отмена"
        okButtonProps={{ danger: true, disabled: !archiveReason.trim(), loading: archiveMutation.isPending }}
      >
        <p>Купон будет скрыт из каталога. Уже купленные сертификаты останутся действительными.</p>
        <Input.TextArea
          rows={3}
          value={archiveReason}
          onChange={(e) => setArchiveReason(e.target.value)}
          placeholder="Укажите причину архивирования (обязательно)"
        />
      </Modal>
    </div>
  );
};
