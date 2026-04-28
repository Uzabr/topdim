import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Card, Table, Button, Modal, Form, Input, Select, Typography,
  Tag, Space, message, Popconfirm, Spin, Result
} from 'antd';
import { PlusOutlined, DeleteOutlined, UserOutlined } from '@ant-design/icons';
import dayjs from 'dayjs';
import api from '../api';

const { Title } = Typography;
const { Option } = Select;

interface Staff {
  id: number;
  name: string;
  phone: string;
  role: string;
  active: boolean;
  merchantLocationId: number | null;
  loginEmail: string | null;
  createdAt: string;
}

interface Location {
  id: number;
  title: string;
  address: string;
}

const fetchStaff = async (): Promise<Staff[]> => {
  const res = await api.get('/api/v1/partner/staff');
  return res.data.data;
};

const fetchLocations = async (): Promise<Location[]> => {
  const res = await api.get('/api/v1/partner/merchant/locations');
  return res.data.data;
};

export default function StaffPage() {
  const queryClient = useQueryClient();
  const [modalOpen, setModalOpen] = useState(false);
  const [form] = Form.useForm();

  const { data: staff, isLoading, error } = useQuery({ queryKey: ['staff'], queryFn: fetchStaff });
  const { data: locations } = useQuery({ queryKey: ['locations'], queryFn: fetchLocations });

  const addMutation = useMutation({
    mutationFn: (values: any) => api.post('/api/v1/partner/staff', values),
    onSuccess: () => {
      message.success('Сотрудник добавлен');
      queryClient.invalidateQueries({ queryKey: ['staff'] });
      setModalOpen(false);
      form.resetFields();
    },
    onError: (err: any) => message.error(err.response?.data?.message || 'Ошибка'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/api/v1/partner/staff/${id}`),
    onSuccess: () => {
      message.success('Сотрудник деактивирован');
      queryClient.invalidateQueries({ queryKey: ['staff'] });
    },
    onError: (err: any) => message.error(err.response?.data?.message || 'Ошибка'),
  });

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (error) return <Result status="error" title="Ошибка загрузки" />;

  const columns = [
    { title: 'Имя', dataIndex: 'name', key: 'name', render: (v: string) => <><UserOutlined /> {v}</> },
    { title: 'Телефон', dataIndex: 'phone', key: 'phone' },
    { title: 'Роль', dataIndex: 'role', key: 'role',
      render: (v: string) => <Tag color={v === 'CASHIER' ? 'blue' : 'purple'}>{v === 'CASHIER' ? 'Кассир' : 'Менеджер'}</Tag>
    },
    { title: 'Филиал', dataIndex: 'merchantLocationId', key: 'loc',
      render: (v: number | null) => {
        if (!v) return '—';
        const loc = locations?.find(l => l.id === v);
        return loc ? loc.title : `#${v}`;
      }
    },
    { title: 'Email', dataIndex: 'loginEmail', key: 'email', render: (v: string | null) => v || '—' },
    { title: 'Статус', dataIndex: 'active', key: 'active',
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? 'Активен' : 'Неактивен'}</Tag>
    },
    { title: 'Создан', dataIndex: 'createdAt', key: 'created',
      render: (v: string) => v ? dayjs(v).format('DD.MM.YYYY') : '—'
    },
    {
      title: '', key: 'actions',
      render: (_: any, record: Staff) => record.active ? (
        <Popconfirm
          title="Деактивировать сотрудника?"
          onConfirm={() => deleteMutation.mutate(record.id)}
        >
          <Button danger icon={<DeleteOutlined />} size="small" id={`delete-staff-${record.id}`}>
            Удалить
          </Button>
        </Popconfirm>
      ) : null
    },
  ];

  return (
    <div>
      <Space style={{ width: '100%', justifyContent: 'space-between', marginBottom: 16 }}>
        <Title level={3} style={{ margin: 0 }}>👥 Сотрудники</Title>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)} id="add-staff-btn">
          Добавить
        </Button>
      </Space>

      <Card style={{ borderRadius: 12 }}>
        <Table
          dataSource={staff}
          columns={columns}
          rowKey="id"
          pagination={{ pageSize: 10 }}
          locale={{ emptyText: 'Нет сотрудников' }}
        />
      </Card>

      <Modal
        title="Добавить сотрудника"
        open={modalOpen}
        onCancel={() => { setModalOpen(false); form.resetFields(); }}
        onOk={() => form.submit()}
        confirmLoading={addMutation.isPending}
        okText="Добавить"
        cancelText="Отмена"
      >
        <Form form={form} layout="vertical" onFinish={(values) => addMutation.mutate(values)}>
          <Form.Item name="name" label="Имя" rules={[{ required: true, message: 'Введите имя' }]}>
            <Input id="staff-name" />
          </Form.Item>
          <Form.Item name="phone" label="Телефон" rules={[{ required: true, message: 'Введите телефон' }]}>
            <Input id="staff-phone" />
          </Form.Item>
          <Form.Item name="role" label="Роль" initialValue="CASHIER">
            <Select id="staff-role">
              <Option value="CASHIER">Кассир</Option>
              {/* MANAGER скрыт до полной поддержки (beta) */}
            </Select>
          </Form.Item>
          <Form.Item name="merchantLocationId" label="Филиал (обязательно)" rules={[{ required: true, message: 'Кассир должен быть привязан к филиалу' }]}>
            <Select placeholder="Выберите филиал" id="staff-location">
              {locations?.map((loc) => (
                <Option key={loc.id} value={loc.id}>{loc.title} — {loc.address}</Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="loginEmail" label="Email для входа (обязательно)" rules={[{ required: true, message: 'Email обязателен для кассира' }, { type: 'email', message: 'Некорректный email' }]}>
            <Input placeholder="cashier@example.com" id="staff-email" />
          </Form.Item>
          <Form.Item name="temporaryPassword" label="Временный пароль (обязательно)" rules={[{ required: true, message: 'Пароль обязателен' }, { min: 6, message: 'Минимум 6 символов' }]}>
            <Input.Password placeholder="Мин. 6 символов" id="staff-password" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
