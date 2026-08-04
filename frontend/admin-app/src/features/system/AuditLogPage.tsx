import { useState } from 'react';
import { Button, Empty, Result, Table, Tag, Typography } from 'antd';
import { useQuery } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { ReloadOutlined } from '@ant-design/icons';
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';

const { Title } = Typography;

interface AuditLog {
  id: number;
  userId: number;
  action: string;
  entityName: string;
  entityId: number;
  details: string;
  createdAt: string;
}

const ACTION_LABELS: Record<string, string> = {
  CREATE_ADMIN: 'Создание сотрудника',
  DELETE_USER: 'Удаление пользователя',
  CHANGE_ROLE: 'Смена роли',
  BLOCK_USER: 'Блокировка пользователя',
  UNBLOCK_USER: 'Разблокировка пользователя',
};

const ENTITY_LABELS: Record<string, string> = {
  USER: 'Пользователь',
};

export const AuditLogPage = () => {
  const [page, setPage] = useState(0);

  const { data, error, isLoading, refetch, isFetching } = useQuery({
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
      title: 'Инициатор',
      render: (_, record) => (
        <Typography.Text>ID: {record.userId}</Typography.Text>
      ),
    },
    { 
      title: 'Действие', 
      dataIndex: 'action',
      render: (val) => <Tag color="blue">{ACTION_LABELS[val] ?? val}</Tag>,
    },
    {
      title: 'Объект',
      render: (_, record) => (
        <span>{ENTITY_LABELS[record.entityName] ?? record.entityName} #{record.entityId}</span>
      ),
    },
    { title: 'Детали', dataIndex: 'details' },
    { 
      title: 'Дата и время', 
      dataIndex: 'createdAt',
      render: (date: string) => dayjs(date).format('DD.MM.YY HH:mm:ss'),
      width: 150,
    },
  ];

  if (error) {
    return (
      <Result
        status="error"
        title="Ошибка загрузки аудита сотрудников"
        extra={<Button loading={isFetching} onClick={() => refetch()}>Повторить</Button>}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Аудит сотрудников</Title>
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
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Нет действий с сотрудниками" /> }}
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
