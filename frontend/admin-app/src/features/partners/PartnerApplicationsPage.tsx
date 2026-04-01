import { useState } from 'react';
import { Table, Tag, Button, Space, Typography, message } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import api from '../../api/client';
import type { PartnerApplication, ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

const { Title } = Typography;

const statusColors: Record<string, string> = {
  PENDING: 'orange',
  APPROVED: 'green',
  REJECTED: 'red',
};

export const PartnerApplicationsPage = () => {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['partner-applications', page],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<PartnerApplication>>>(
        '/api/v1/admin/partner-applications',
        { params: { page, size: 20 } }
      );
      return res.data.data;
    },
  });

  const approveMutation = useMutation({
    mutationFn: (id: number) => api.patch(`/api/v1/admin/partner-applications/${id}/approve`),
    onSuccess: () => {
      message.success('Заявка одобрена!');
      queryClient.invalidateQueries({ queryKey: ['partner-applications'] });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: (id: number) => api.patch(`/api/v1/admin/partner-applications/${id}/reject`),
    onSuccess: () => {
      message.info('Заявка отклонена');
      queryClient.invalidateQueries({ queryKey: ['partner-applications'] });
    },
  });

  const columns: ColumnsType<PartnerApplication> = [
    {
      title: 'ID',
      dataIndex: 'id',
      width: 60,
    },
    {
      title: 'Имя',
      render: (_, record) => `${record.firstName} ${record.lastName}`,
    },
    {
      title: 'Телефон',
      dataIndex: 'phone',
    },
    {
      title: 'Компания',
      dataIndex: 'companyName',
    },
    {
      title: 'Комментарий',
      dataIndex: 'comment',
      ellipsis: true,
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      render: (status: string) => (
        <Tag color={statusColors[status]}>{status}</Tag>
      ),
    },
    {
      title: 'Дата',
      dataIndex: 'createdAt',
      render: (date: string) => dayjs(date).format('DD.MM.YYYY HH:mm'),
    },
    {
      title: 'Действия',
      width: 220,
      render: (_, record) => {
        if (record.status !== 'PENDING') return <Tag>{record.status}</Tag>;
        return (
          <Space>
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              size="small"
              loading={approveMutation.isPending}
              onClick={() => approveMutation.mutate(record.id)}
            >
              Одобрить
            </Button>
            <Button
              danger
              icon={<CloseCircleOutlined />}
              size="small"
              loading={rejectMutation.isPending}
              onClick={() => rejectMutation.mutate(record.id)}
            >
              Отклонить
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <div>
      <Title level={4}>Заявки на партнёрство</Title>
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
