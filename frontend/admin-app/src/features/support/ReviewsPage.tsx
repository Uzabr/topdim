import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Card, Table, Tag, Typography, Button, Modal, Input, Space, Spin, Result, Rate, message } from 'antd';
import { CheckOutlined, CloseOutlined, MessageOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { getPendingReviews, reviewUserReview, type AdminReview } from './api';

const { Title, Text, Paragraph } = Typography;

export function ReviewsPage() {
  const [page, setPage] = useState(0);
  const [rejectModal, setRejectModal] = useState<{ visible: boolean; reviewId: number | null }>({
    visible: false, reviewId: null,
  });
  const [rejectReason, setRejectReason] = useState('');
  const queryClient = useQueryClient();

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-pending-reviews', page],
    queryFn: () => getPendingReviews(page, 20),
  });

  const approveMutation = useMutation({
    mutationFn: (id: number) => reviewUserReview(id, 'APPROVE'),
    onSuccess: () => {
      message.success('Отзыв одобрен');
      queryClient.invalidateQueries({ queryKey: ['admin-pending-reviews'] });
    },
    onError: () => message.error('Ошибка при одобрении'),
  });

  const rejectMutation = useMutation({
    mutationFn: ({ id, reason }: { id: number; reason: string }) =>
      reviewUserReview(id, 'REJECT', reason),
    onSuccess: () => {
      message.success('Отзыв отклонён');
      setRejectModal({ visible: false, reviewId: null });
      setRejectReason('');
      queryClient.invalidateQueries({ queryKey: ['admin-pending-reviews'] });
    },
    onError: () => message.error('Ошибка при отклонении'),
  });

  if (error) return <Result status="error" title="Ошибка загрузки отзывов" />;

  const columns = [
    {
      title: 'ID', dataIndex: 'id', key: 'id', width: 60,
    },
    {
      title: 'Купон', dataIndex: 'couponOfferId', key: 'couponOfferId', width: 80,
      render: (v: number) => <Tag>#{v}</Tag>,
    },
    {
      title: 'Автор', key: 'author',
      render: (_: any, r: AdminReview) => (
        <span>{r.userName || 'Пользователь'} <Text type="secondary">(#{r.userId})</Text></span>
      ),
    },
    {
      title: 'Оценка', dataIndex: 'rating', key: 'rating', width: 140,
      render: (v: number) => <Rate disabled defaultValue={v} style={{ fontSize: 14 }} />,
    },
    {
      title: 'Комментарий', dataIndex: 'comment', key: 'comment',
      render: (v: string) => (
        <Paragraph ellipsis={{ rows: 2, expandable: true, symbol: 'ещё' }} style={{ margin: 0 }}>
          {v}
        </Paragraph>
      ),
    },
    {
      title: 'Дата', dataIndex: 'createdAt', key: 'date', width: 130,
      render: (v: string) => v ? dayjs(v).format('DD.MM.YYYY HH:mm') : '—',
    },
    {
      title: 'Действия', key: 'actions', width: 200,
      render: (_: any, r: AdminReview) => (
        <Space>
          <Button
            type="primary" size="small"
            icon={<CheckOutlined />}
            loading={approveMutation.isPending}
            onClick={() => approveMutation.mutate(r.id)}
            id={`approve-review-${r.id}`}
          >
            Одобрить
          </Button>
          <Button
            danger size="small"
            icon={<CloseOutlined />}
            onClick={() => setRejectModal({ visible: true, reviewId: r.id })}
            id={`reject-review-${r.id}`}
          >
            Отклонить
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Title level={3} style={{ marginBottom: 16 }}>
        <MessageOutlined /> Модерация отзывов
      </Title>

      <Card style={{ borderRadius: 12 }}>
        {isLoading ? (
          <Spin size="large" style={{ display: 'block', margin: '60px auto' }} />
        ) : (
          <Table
            dataSource={data?.content || []}
            columns={columns}
            rowKey="id"
            pagination={{
              current: (data?.number ?? 0) + 1,
              total: data?.totalElements ?? 0,
              pageSize: data?.size ?? 20,
              onChange: (p) => setPage(p - 1),
              showSizeChanger: false,
            }}
            locale={{ emptyText: 'Нет отзывов на модерации 🎉' }}
          />
        )}
      </Card>

      {/* Reject Modal */}
      <Modal
        title="Причина отклонения"
        open={rejectModal.visible}
        onOk={() => {
          if (!rejectReason.trim()) {
            message.warning('Укажите причину отклонения');
            return;
          }
          if (rejectModal.reviewId) {
            rejectMutation.mutate({ id: rejectModal.reviewId, reason: rejectReason.trim() });
          }
        }}
        onCancel={() => {
          setRejectModal({ visible: false, reviewId: null });
          setRejectReason('');
        }}
        confirmLoading={rejectMutation.isPending}
        okText="Отклонить"
        cancelText="Отмена"
        okButtonProps={{ danger: true }}
      >
        <Input.TextArea
          placeholder="Укажите причину отклонения (будет видна автору)"
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          rows={3}
          maxLength={500}
          id="reject-reason-input"
        />
      </Modal>
    </div>
  );
}
