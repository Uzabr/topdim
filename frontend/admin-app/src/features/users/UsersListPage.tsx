import { useState } from 'react';
import {
  Table, Tag, Input, Select, Typography, Space, Button, App,
} from 'antd';
import {
  SearchOutlined, CheckCircleOutlined, CloseCircleOutlined,
  LockOutlined, UnlockOutlined, MailOutlined, PhoneOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchUsersPage, blockUser } from './api';
import type { AdminUser } from './api';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';

const { Title } = Typography;

const ROLE_LABELS: Record<string, { label: string; color: string }> = {
  USER: { label: 'Пользователь', color: 'blue' },
  PARTNER: { label: 'Партнёр', color: 'purple' },
  MODERATOR: { label: 'Модератор', color: 'orange' },
  ADMIN: { label: 'Админ', color: 'red' },
  SUPER_ADMIN: { label: 'Супер-админ', color: 'volcano' },
};

export const UsersListPage = () => {
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState('');
  const [roleFilter, setRoleFilter] = useState<string | undefined>(undefined);
  const queryClient = useQueryClient();
  const { message } = App.useApp();

  const { data, isLoading } = useQuery({
    queryKey: ['admin-users', page, search, roleFilter],
    queryFn: () =>
      fetchUsersPage({
        page,
        size: 20,
        search: search || undefined,
        role: roleFilter,
      }),
  });

  const blockMutation = useMutation({
    mutationFn: ({ id, blocked }: { id: number; blocked: boolean }) =>
      blockUser(id, blocked),
    onSuccess: (_, { blocked }) => {
      message.success(blocked ? 'Пользователь заблокирован' : 'Пользователь разблокирован');
      queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    },
    onError: () => {
      message.error('Не удалось выполнить операцию');
    },
  });

  const columns: ColumnsType<AdminUser> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Пользователь',
      render: (_, record) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.firstName} {record.lastName || ''}</div>
          <div style={{ fontSize: 12, color: '#888' }}>
            <MailOutlined style={{ marginRight: 4 }} />
            {record.email}
          </div>
          {record.phone && (
            <div style={{ fontSize: 12, color: '#888' }}>
              <PhoneOutlined style={{ marginRight: 4 }} />
              {record.phone}
            </div>
          )}
        </div>
      ),
    },
    {
      title: 'Роль',
      dataIndex: 'role',
      width: 140,
      render: (role: string) => {
        const info = ROLE_LABELS[role] || { label: role, color: 'default' };
        return <Tag color={info.color}>{info.label}</Tag>;
      },
    },
    {
      title: 'Статус',
      dataIndex: 'enabled',
      width: 120,
      render: (enabled: boolean) =>
        enabled ? (
          <Tag icon={<CheckCircleOutlined />} color="success">Активен</Tag>
        ) : (
          <Tag icon={<CloseCircleOutlined />} color="error">Заблокирован</Tag>
        ),
    },
    {
      title: 'Верификация',
      width: 140,
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <Tag color={record.emailVerified ? 'green' : 'default'} style={{ fontSize: 11 }}>
            Email: {record.emailVerified ? '✓' : '✗'}
          </Tag>
          <Tag color={record.phoneVerified ? 'green' : 'default'} style={{ fontSize: 11 }}>
            Телефон: {record.phoneVerified ? '✓' : '✗'}
          </Tag>
        </Space>
      ),
    },
    {
      title: 'Регистрация',
      dataIndex: 'createdAt',
      width: 130,
      render: (date: string) =>
        date ? dayjs(date).format('DD.MM.YYYY HH:mm') : '—',
    },
    {
      title: 'Действия',
      width: 140,
      render: (_, record) => (
        <Button
          type={record.enabled ? 'default' : 'primary'}
          danger={record.enabled}
          size="small"
          icon={record.enabled ? <LockOutlined /> : <UnlockOutlined />}
          loading={blockMutation.isPending}
          onClick={() =>
            blockMutation.mutate({ id: record.id, blocked: record.enabled })
          }
        >
          {record.enabled ? 'Заблокировать' : 'Разблокировать'}
        </Button>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>Все пользователи</Title>
      </div>

      <Space style={{ marginBottom: 16 }} wrap>
        <Input
          placeholder="Поиск по имени, email, телефону..."
          prefix={<SearchOutlined />}
          value={search}
          onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          style={{ width: 320 }}
          allowClear
        />
        <Select
          placeholder="Роль"
          value={roleFilter}
          onChange={(v) => { setRoleFilter(v); setPage(0); }}
          style={{ width: 180 }}
          allowClear
          options={[
            { value: 'USER', label: 'Пользователи' },
            { value: 'PARTNER', label: 'Партнёры' },
            { value: 'MODERATOR', label: 'Модераторы' },
            { value: 'ADMIN', label: 'Администраторы' },
            { value: 'SUPER_ADMIN', label: 'Супер-админы' },
          ]}
        />
      </Space>

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
        scroll={{ x: 900 }}
      />
    </div>
  );
};
