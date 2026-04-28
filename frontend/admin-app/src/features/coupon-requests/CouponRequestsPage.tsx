import { useState } from 'react';
import { Table, Tag, Button, Space, Typography, Modal, Input, App as AntApp, Badge, Tooltip } from 'antd';
import {
  StopOutlined, EyeOutlined,
  PlayCircleOutlined, ExclamationCircleOutlined, SendOutlined
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

interface CouponRequest {
  id: number;
  title: string;
  offerDescription: string;
  oldPrice: number | null;
  fromPrice: number;
  discountPercent: number | null;
  status: string;
  coverImageUrl: string | null;
  merchant: { id: number; name: string; logoUrl?: string } | null;
  category: { id: number; name: string; slug: string } | null;
  assignedModeratorId: number | null;
  assignedModeratorName: string | null;
  archiveReason: string | null;
  revisionComment: string | null;
  options: { id: number; title: string; regularPrice: number; couponPrice: number; quantityLimit: number }[];
  images: string[];
  createdAt: string;
}

const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  LEAD:                  { color: 'purple',  label: 'Новая заявка' },
  DRAFT:                 { color: 'blue',    label: 'В работе' },
  REVISION_REQUESTED:    { color: 'gold',    label: 'Нужны уточнения' },
  WAITING_FOR_MERCHANT:  { color: 'orange',  label: 'На согласовании' },
  ACTIVE:                { color: 'green',   label: 'Опубликована' },
  ARCHIVED:              { color: 'default', label: 'Отклонена/Архив' },
};

export function CouponRequestsPage() {
  const queryClient = useQueryClient();
  const { message: antMessage, modal } = AntApp.useApp();
  const [statusFilter, setStatusFilter] = useState<string>('LEAD');
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedRequest, setSelectedRequest] = useState<CouponRequest | null>(null);

  const { data, isLoading } = useQuery<PageResponse<CouponRequest>>({
    queryKey: ['admin-coupon-requests', statusFilter],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<CouponRequest>>>('/api/v1/admin/coupons', {
        params: { status: statusFilter || undefined, page: 0, size: 50 },
      });
      return res.data.data;
    },
  });

  const takeToWorkMutation = useMutation({
    mutationFn: (id: number) => api.patch(`/api/v1/admin/coupons/${id}/take-to-work`),
    onSuccess: () => {
      antMessage.success('Заявка взята в работу');
      queryClient.invalidateQueries({ queryKey: ['admin-coupon-requests'] });
    },
    onError: (e: any) => antMessage.error(e.response?.data?.message || 'Ошибка'),
  });

  const sendToApprovalMutation = useMutation({
    mutationFn: (id: number) => api.post(`/api/v1/admin/coupons/${id}/send-to-approval`),
    onSuccess: () => {
      antMessage.success('Отправлено на согласование мерчанту');
      queryClient.invalidateQueries({ queryKey: ['admin-coupon-requests'] });
    },
    onError: (e: any) => antMessage.error(e.response?.data?.message || 'Ошибка'),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      api.post(`/api/v1/admin/coupons/${id}/reject-request`, { reason }),
    onSuccess: () => {
      antMessage.success('Заявка отклонена');
      queryClient.invalidateQueries({ queryKey: ['admin-coupon-requests'] });
    },
    onError: (e: any) => antMessage.error(e.response?.data?.message || 'Ошибка'),
  });

  const handleReject = (id: number) => {
    let reason = '';
    modal.confirm({
      title: 'Отклонить заявку',
      content: (
        <TextArea
          rows={3}
          placeholder="Укажите причину отклонения"
          onChange={e => { reason = e.target.value; }}
        />
      ),
      okText: 'Отклонить',
      okType: 'danger',
      onOk: () => {
        if (!reason.trim()) {
          antMessage.warning('Укажите причину отклонения');
          return Promise.reject();
        }
        return rejectMutation.mutateAsync({ id, reason });
      },
    });
  };

  const columns: ColumnsType<CouponRequest> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
    },
    {
      title: 'Обложка',
      key: 'cover',
      width: 70,
      render: (_, r) => r.coverImageUrl
        ? <img src={r.coverImageUrl} alt="" style={{ width: 48, height: 48, objectFit: 'cover', borderRadius: 6 }} />
        : <Badge count="!" color="orange"><div style={{ width: 48, height: 48, background: '#f0f0f0', borderRadius: 6 }} /></Badge>,
    },
    {
      title: 'Название',
      dataIndex: 'title',
      key: 'title',
      ellipsis: true,
    },
    {
      title: 'Мерчант',
      key: 'merchant',
      render: (_, r) => r.merchant?.name || '—',
      ellipsis: true,
    },
    {
      title: 'Категория',
      key: 'category',
      render: (_, r) => r.category?.name || '—',
    },
    {
      title: 'Статус',
      key: 'status',
      render: (_, r) => {
        const cfg = STATUS_CONFIG[r.status] || { color: 'default', label: r.status };
        return <Tag color={cfg.color}>{cfg.label}</Tag>;
      },
    },
    {
      title: 'Модератор',
      key: 'moderator',
      render: (_, r) => r.assignedModeratorName || <Text type="secondary">—</Text>,
    },
    {
      title: 'Дата',
      key: 'createdAt',
      width: 100,
      render: (_, r) => dayjs(r.createdAt).format('DD.MM.YY'),
    },
    {
      title: 'Действия',
      key: 'actions',
      width: 220,
      render: (_, r) => (
        <Space size="small">
          <Tooltip title="Детали">
            <Button icon={<EyeOutlined />} size="small" onClick={() => { setSelectedRequest(r); setDetailOpen(true); }} />
          </Tooltip>
          {r.status === 'LEAD' && (
            <Tooltip title="Взять в работу">
              <Button
                icon={<PlayCircleOutlined />}
                size="small"
                type="primary"
                ghost
                onClick={() => takeToWorkMutation.mutate(r.id)}
                loading={takeToWorkMutation.isPending}
              />
            </Tooltip>
          )}
          {r.status === 'DRAFT' && (
            <Tooltip title="На согласование">
              <Button
                icon={<SendOutlined />}
                size="small"
                type="primary"
                onClick={() => sendToApprovalMutation.mutate(r.id)}
                loading={sendToApprovalMutation.isPending}
              />
            </Tooltip>
          )}
          {(r.status === 'LEAD' || r.status === 'DRAFT') && (
            <Tooltip title="Отклонить">
              <Button
                icon={<StopOutlined />}
                size="small"
                danger
                onClick={() => handleReject(r.id)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ];

  const requests = data?.content || [];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>📋 Заявки на акции от партнёров</Title>
      </div>

      <Space style={{ marginBottom: 16 }}>
        {['LEAD', 'DRAFT', 'WAITING_FOR_MERCHANT', 'ARCHIVED', ''].map(s => (
          <Button
            key={s}
            type={statusFilter === s ? 'primary' : 'default'}
            size="small"
            onClick={() => setStatusFilter(s)}
          >
            {s ? (STATUS_CONFIG[s]?.label || s) : 'Все'}
          </Button>
        ))}
      </Space>

      <Table
        dataSource={requests}
        columns={columns}
        rowKey="id"
        loading={isLoading}
        pagination={requests.length > 20 ? { pageSize: 20 } : false}
        size="middle"
      />

      <Modal
        title={selectedRequest ? `Заявка #${selectedRequest.id}: ${selectedRequest.title}` : 'Детали'}
        open={detailOpen}
        onCancel={() => setDetailOpen(false)}
        footer={null}
        width={700}
      >
        {selectedRequest && (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <div>
              <Text strong>Мерчант: </Text>
              <Text>{selectedRequest.merchant?.name || '—'}</Text>
            </div>
            <div>
              <Text strong>Категория: </Text>
              <Text>{selectedRequest.category?.name || '—'}</Text>
            </div>
            <div>
              <Text strong>Описание: </Text>
              <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{selectedRequest.offerDescription}</Paragraph>
            </div>
            <div>
              <Text strong>Цена: </Text>
              <Text>{selectedRequest.fromPrice?.toLocaleString('ru-RU')} сум</Text>
              {selectedRequest.oldPrice && (
                <Text delete type="secondary" style={{ marginLeft: 8 }}>
                  {selectedRequest.oldPrice.toLocaleString('ru-RU')} сум
                </Text>
              )}
              {selectedRequest.discountPercent && (
                <Tag color="red" style={{ marginLeft: 8 }}>-{selectedRequest.discountPercent}%</Tag>
              )}
            </div>
            {selectedRequest.options && selectedRequest.options.length > 0 && (
              <div>
                <Text strong>Варианты: </Text>
                <ul>
                  {selectedRequest.options.map((o, i) => (
                    <li key={i}>{o.title}: {o.couponPrice?.toLocaleString('ru-RU')} сум (обычная: {o.regularPrice?.toLocaleString('ru-RU')}), лимит: {o.quantityLimit || '∞'}</li>
                  ))}
                </ul>
              </div>
            )}
            {selectedRequest.images && selectedRequest.images.length > 0 && (
              <div>
                <Text strong>Фото от партнёра:</Text>
                <Space wrap style={{ marginTop: 8 }}>
                  {selectedRequest.images.map((url, i) => (
                    <img key={i} src={url} alt={`Фото ${i+1}`} style={{ width: 100, height: 100, objectFit: 'cover', borderRadius: 8 }} />
                  ))}
                </Space>
              </div>
            )}
            {!selectedRequest.coverImageUrl && (
              <Tag icon={<ExclamationCircleOutlined />} color="warning">
                Нет обложки — при отправке на согласование будет подставлена категорийная
              </Tag>
            )}
            {selectedRequest.archiveReason && (
              <div>
                <Text strong type="danger">Причина отклонения: </Text>
                <Text>{selectedRequest.archiveReason}</Text>
              </div>
            )}
          </Space>
        )}
      </Modal>
    </div>
  );
}
