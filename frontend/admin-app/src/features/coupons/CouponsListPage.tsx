import { useState } from 'react';
import { Table, Tag, Button, Space, Typography, Popconfirm, message } from 'antd';
import { EditOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';

const { Title } = Typography;

export const CouponsListPage = () => {
  const [page, setPage] = useState(0);
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-coupons', page],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<any>>>(
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
    ACTIVE: 'green',
    DRAFT: 'default',
    PAUSED: 'orange',
    ENDED: 'red',
  };

  const columns: ColumnsType<any> = [
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
      width: 120,
      render: (_, record) => (
        <Space>
          <Button type="text" icon={<EditOutlined />} onClick={() => message.info('Редактирование пока не реализовано')} />
          <Popconfirm
            title="Удалить купон?"
            description="Это действие необратимо."
            onConfirm={() => deleteMutation.mutate(record.id)}
            okText="Да"
            cancelText="Нет"
          >
            <Button type="text" danger icon={<DeleteOutlined />} loading={deleteMutation.isPending} />
          </Popconfirm>
        </Space>
      ),
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
    </div>
  );
};
