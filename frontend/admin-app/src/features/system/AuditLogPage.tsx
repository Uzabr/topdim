import { useState } from 'react';
import { Table, Typography, Tag, Space, Button } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { ReloadOutlined } from '@ant-design/icons';
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';

const { Title } = Typography;

interface AuditLog {
  id: number;
  adminId: number;
  adminEmail: string;
  action: string;
  details: string;
  ipAddress: string;
  createdAt: string;
}

export const AuditLogPage = () => {
  const [page, setPage] = useState(0);

  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['audit-logs', page],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<AuditLog>>>(
        '/api/v1/super/audit-logs',
        { params: { page, size: 20 } }
      );
      return res.data.data;
    },
  });

  const columns: ColumnsType<AuditLog> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { 
      title: 'Администратор', 
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <span>{record.adminEmail}</span>
          <Typography.Text type="secondary" style={{ fontSize: '12px' }}>
            ID: {record.adminId}
          </Typography.Text>
        </Space>
      ),
    },
    { 
      title: 'Действие', 
      dataIndex: 'action',
      render: (val) => <Tag color="blue">{val}</Tag>,
    },
    { title: 'Детали', dataIndex: 'details' },
    { title: 'IP Адрес', dataIndex: 'ipAddress' },
    { 
      title: 'Дата и время', 
      dataIndex: 'createdAt',
      render: (date: string) => dayjs(date).format('DD.MM.YY HH:mm:ss'),
      width: 150,
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Журнал аудита</Title>
        <Button 
          icon={<ReloadOutlined />} 
          onClick={() => refetch()} 
          loading={isFetching}
        >
          Обновить
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
          showTotal: (total) => `Всего записей: ${total}`,
        }}
      />
    </div>
  );
};
