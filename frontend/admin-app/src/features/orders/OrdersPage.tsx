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
  CANCELLED: 'red',
  REFUNDED: 'purple',
};

const statusLabels: Record<string, string> = {
  PENDING: 'Ожидает оплаты',
  PAID: 'Оплачен',
  CANCELLED: 'Отменён',
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
      title: 'ID', dataIndex: 'id', key: 'id', width: 80,
    },
    {
      title: 'Пользователь', key: 'user',
      render: (_: any, r: AdminOrder) => (
        <span>#{r.userId} — {r.userEmail || '—'}</span>
      ),
    },
    {
      title: 'Телефон', dataIndex: 'userPhone', key: 'phone',
    },
    {
      title: 'Статус', dataIndex: 'status', key: 'status',
      render: (v: string) => <Tag color={statusColors[v] || 'default'}>{statusLabels[v] || v}</Tag>,
    },
    {
      title: 'Сумма', dataIndex: 'totalAmount', key: 'amount',
      render: (v: number) => v ? `${v.toLocaleString('ru-RU')} сум` : '—',
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
            { label: 'Отменён', value: 'CANCELLED' },
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
