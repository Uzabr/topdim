import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Card, Table, Tag, Typography, Select, Space, Spin, Result } from 'antd';
import { ShoppingCartOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import { fetchAdminOrders, type AdminOrder } from './api';

const { Title } = Typography;

const statusColors: Record<string, string> = {
  PENDING: 'gold',
  PAID: 'green',
  COMPLETED: 'blue',
  CANCELLED: 'red',
  REFUND_REQUESTED: 'orange',
  REFUNDED: 'purple',
};

const statusLabels: Record<string, string> = {
  PENDING: 'Ожидает оплаты',
  PAID: 'Оплачен',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
  REFUND_REQUESTED: 'Запрошен возврат',
  REFUNDED: 'Возвращён',
};

export function OrdersPage() {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string | undefined>(undefined);

  const { data, isLoading, error } = useQuery({
    queryKey: ['admin-orders', page, statusFilter],
    queryFn: () => fetchAdminOrders(page, 20, statusFilter),
  });

  if (error) return <Result status="error" title="Ошибка загрузки заказов" />;

  const columns = [
    {
      title: 'Заказ', dataIndex: 'orderNumber', key: 'orderNumber', width: 180,
      render: (orderNumber: string, record: AdminOrder) => orderNumber || `#${record.id}`,
    },
    {
      title: 'Пользователь', key: 'user',
      render: (_: unknown, r: AdminOrder) => (
        <span>#{r.userId} — {r.userEmail || '—'}</span>
      ),
    },
    {
      title: 'Телефон', dataIndex: 'userPhone', key: 'phone',
      render: (phone: string | null) => phone || '—',
    },
    {
      title: 'Статус', dataIndex: 'status', key: 'status',
      render: (v: string) => <Tag color={statusColors[v] || 'default'}>{statusLabels[v] || v}</Tag>,
    },
    {
      title: 'Сумма', dataIndex: 'totalAmount', key: 'amount',
      render: (v: number | null) => v == null ? '—' : `${v.toLocaleString('ru-RU')} сум`,
    },
    {
      title: 'Дата', dataIndex: 'createdAt', key: 'date',
      render: (v: string) => v ? dayjs(v).format('DD.MM.YYYY HH:mm') : '—',
    },
  ];

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}><ShoppingCartOutlined /> Заказы</Title>
        <Select
          style={{ width: 200 }}
          placeholder="Фильтр по статусу"
          allowClear
          value={statusFilter}
          onChange={(v) => { setStatusFilter(v); setPage(0); }}
          options={[
            { label: 'Все', value: undefined },
            { label: 'Ожидает оплаты', value: 'PENDING' },
            { label: 'Оплачен', value: 'PAID' },
            { label: 'Завершён', value: 'COMPLETED' },
            { label: 'Отменён', value: 'CANCELLED' },
            { label: 'Запрошен возврат', value: 'REFUND_REQUESTED' },
            { label: 'Возвращён', value: 'REFUNDED' },
          ]}
          id="order-status-filter"
        />
      </Space>

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
            locale={{ emptyText: 'Нет заказов' }}
          />
        )}
      </Card>
    </div>
  );
}
