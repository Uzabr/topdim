import { useState } from 'react';
import { App, Button, Empty, Form, Input, Modal, Popconfirm, Result, Select, Space, Table, Tabs, Tag, Typography } from 'antd';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { ReloadOutlined, PlusOutlined, LockOutlined, UnlockOutlined, SwapOutlined } from '@ant-design/icons';
import api from '../../api/client';
import type { ApiResponse, PageResponse } from '../../types';
import type { ColumnsType } from 'antd/es/table';

const { Title } = Typography;

const STAFF_ROLE_LABELS: Record<string, string> = {
  ADMIN: 'Администратор',
  MODERATOR: 'Модератор',
};

const STRONG_PASSWORD_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()\-_=+])[A-Za-z\d@$!%*?&#^()\-_=+]{8,128}$/;
const STRONG_PASSWORD_MESSAGE = '8–128 символов: заглавная и строчная буквы, цифра и спецсимвол';

interface StaffMember {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  role: string;
  enabled: boolean;
  createdAt: string;
}

export const StaffPage = () => {
  const { message } = App.useApp();
  const [activeTab, setActiveTab] = useState('ADMIN');
  const [page, setPage] = useState(0);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [roleModalVisible, setRoleModalVisible] = useState(false);
  const [selectedUserId, setSelectedUserId] = useState<number | null>(null);

  const [form] = Form.useForm();
  const [roleForm] = Form.useForm();
  const queryClient = useQueryClient();

  const { data, error, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['staff', activeTab, page],
    queryFn: async () => {
      const res = await api.get<ApiResponse<PageResponse<StaffMember>>>(
        '/api/v1/super/staff',
        { params: { role: activeTab, page, size: 20 } }
      );
      return res.data.data;
    },
  });

  const blockMutation = useMutation({
    mutationFn: ({ id, blocked }: { id: number; blocked: boolean }) =>
      api.patch(`/api/v1/super/users/${id}/block`, { blocked }),
    onSuccess: (res) => {
      message.success(res.data.message || 'Статус обновлен');
      queryClient.invalidateQueries({ queryKey: ['staff'] });
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: { message?: string } } };
      message.error(error.response?.data?.message || 'Ошибка обновления статуса');
    },
  });

  const createMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) => api.post('/api/v1/super/admins', values),
    onSuccess: () => {
      message.success('Сотрудник успешно создан');
      setIsModalOpen(false);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['staff'] });
    },
    onError: (err: unknown) => {
      const error = err as {
        response?: { data?: { message?: string; data?: { password?: string } } };
      };
      message.error(
        error.response?.data?.data?.password
          || error.response?.data?.message
          || 'Ошибка создания сотрудника',
      );
    },
  });

  const changeRoleMutation = useMutation({
    mutationFn: ({ id, newRole }: { id: number; newRole: string }) =>
      api.patch(`/api/v1/super/users/${id}/role`, { newRole }),
    onSuccess: () => {
      message.success('Роль успешно изменена');
      setRoleModalVisible(false);
      setSelectedUserId(null);
      roleForm.resetFields();
      queryClient.invalidateQueries({ queryKey: ['staff'] });
    },
    onError: (err: unknown) => {
      const error = err as { response?: { data?: { message?: string } } };
      message.error(error.response?.data?.message || 'Ошибка смены роли');
    },
  });

  const handleCreateStaff = () => {
    void form.validateFields()
      .then((values) => createMutation.mutate(values))
      .catch(() => undefined);
  };

  const handleChangeRole = () => {
    void roleForm.validateFields()
      .then((values) => {
        if (selectedUserId) {
          changeRoleMutation.mutate({ id: selectedUserId, newRole: values.newRole });
        }
      })
      .catch(() => undefined);
  };

  const openRoleModal = (id: number, currentRole: string) => {
    setSelectedUserId(id);
    roleForm.setFieldsValue({ newRole: currentRole });
    setRoleModalVisible(true);
  };

  const columns: ColumnsType<StaffMember> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Сотрудник',
      render: (_, record) => (
        <Space orientation="vertical" size={0}>
          <strong>{record.firstName} {record.lastName}</strong>
          <span style={{ fontSize: '12px', color: '#666' }}>{record.email}</span>
        </Space>
      ),
    },
    { title: 'Телефон', dataIndex: 'phone' },
    {
      title: 'Роль',
      dataIndex: 'role',
      render: (role) => (
        <Tag color={role === 'ADMIN' ? 'gold' : role === 'SUPER_ADMIN' ? 'magenta' : 'purple'}>
          {STAFF_ROLE_LABELS[role] ?? role}
        </Tag>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'enabled',
      render: (enabled: boolean) => (
        <Tag color={enabled ? 'green' : 'red'}>
          {enabled ? 'Активен' : 'Заблокирован'}
        </Tag>
      ),
    },
    {
      title: 'Создан',
      dataIndex: 'createdAt',
      render: (date: string) => dayjs(date).format('DD.MM.YY HH:mm'),
    },
    {
      title: 'Действия',
      width: 180,
      render: (_, record) => (
        <Space>
          <Button 
            type="text" 
            title="Сменить роль"
            icon={<SwapOutlined />} 
            onClick={() => openRoleModal(record.id, record.role)}
            disabled={record.role === 'SUPER_ADMIN'}
          />
          <Popconfirm
            title={!record.enabled ? "Разблокировать сотрудника?" : "Заблокировать сотрудника?"}
            description={!record.enabled ? "Сотрудник сможет снова заходить в панель." : "Сотрудник не сможет зайти в панель."}
            onConfirm={() => blockMutation.mutate({ id: record.id, blocked: record.enabled })}
            okText="Да"
            cancelText="Нет"
            placement="topLeft"
          >
            <Button
              type="text"
              danger={record.enabled}
              title={!record.enabled ? "Разблокировать" : "Заблокировать"}
              icon={!record.enabled ? <UnlockOutlined /> : <LockOutlined />}
              loading={blockMutation.isPending && blockMutation.variables?.id === record.id}
              disabled={record.role === 'SUPER_ADMIN'}
            />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  if (error) {
    return (
      <Result
        status="error"
        title="Ошибка загрузки сотрудников"
        extra={<Button loading={isFetching} onClick={() => refetch()}>Повторить</Button>}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Управление персоналом</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()} loading={isFetching}>Обновить</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setIsModalOpen(true)}>
            Добавить сотрудника
          </Button>
        </Space>
      </div>

      <Tabs 
        activeKey={activeTab} 
        onChange={(k) => { setActiveTab(k); setPage(0); }}
        items={[
          { key: 'ADMIN', label: 'Администраторы' },
          { key: 'MODERATOR', label: 'Модераторы' },
        ]} 
      />

      <Table
        columns={columns}
        dataSource={data?.content}
        loading={isLoading}
        rowKey="id"
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Нет сотрудников" /> }}
        pagination={{
          current: page + 1,
          pageSize: 20,
          total: data?.totalElements,
          onChange: (p) => setPage(p - 1),
          showTotal: (total) => `Всего: ${total}`,
        }}
      />

      {/* Модальное окно создания сотрудника */}
      <Modal
        title="Новый сотрудник"
        open={isModalOpen}
        onOk={handleCreateStaff}
        onCancel={() => { setIsModalOpen(false); form.resetFields(); }}
        confirmLoading={createMutation.isPending}
        okText="Создать"
        cancelText="Отмена"
      >
        <Form form={form} layout="vertical" initialValues={{ role: 'MODERATOR' }}>
          <Form.Item name="email" label="Email" rules={[{ required: true, type: 'email' }]}>
            <Input placeholder="example@topdim.uz" />
          </Form.Item>
          <Form.Item
            name="password"
            label="Пароль"
            extra={STRONG_PASSWORD_MESSAGE}
            rules={[
              { required: true, message: 'Введите пароль' },
              { pattern: STRONG_PASSWORD_PATTERN, message: STRONG_PASSWORD_MESSAGE },
            ]}
          >
            <Input.Password placeholder="Надёжный пароль" maxLength={128} />
          </Form.Item>
          <Form.Item name="firstName" label="Имя" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="lastName" label="Фамилия">
            <Input />
          </Form.Item>
          <Form.Item name="phone" label="Телефон" rules={[{ required: true }]}>
            <Input placeholder="+998901234567" />
          </Form.Item>
          <Form.Item name="role" label="Роль" rules={[{ required: true }]}>
            <Select style={{ width: '100%' }}>
              <Select.Option value="MODERATOR">Модератор</Select.Option>
              <Select.Option value="ADMIN">Администратор</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>

      {/* Модальное окно смены роли */}
      <Modal
        title="Изменение роли сотрудника"
        open={roleModalVisible}
        onOk={handleChangeRole}
        onCancel={() => { setRoleModalVisible(false); roleForm.resetFields(); }}
        confirmLoading={changeRoleMutation.isPending}
        okText="Сохранить"
        cancelText="Отмена"
      >
        <Form form={roleForm} layout="vertical">
          <Form.Item name="newRole" label="Новая роль" rules={[{ required: true }]}>
            <Select style={{ width: '100%' }}>
              <Select.Option value="MODERATOR">Модератор</Select.Option>
              <Select.Option value="ADMIN">Администратор</Select.Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
