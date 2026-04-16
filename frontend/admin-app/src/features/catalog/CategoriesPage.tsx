import { useState } from 'react';
import { Table, Button, Space, Typography, Tag, Upload, message, Modal, Form, Input } from 'antd';
import { UploadOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import type { UploadProps } from 'antd';
import api from '../../api/client';
import { useAuthStore } from '../../store/authStore';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

interface Category {
  id: number;
  name: string;
  nameUz: string;
  slug: string;
  iconUrl: string;
  sortOrder: number;
}

export const CategoriesPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [form] = Form.useForm();
  const queryClient = useQueryClient();

  // Получение категорий
  const { data, isLoading, refetch, isFetching } = useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const res = await api.get('/api/v1/categories');
      return res.data.data;
    },
  });

  // Создание одной категории вручную
  const createMutation = useMutation({
    mutationFn: (values: any) => api.post('/api/v1/admin/categories', values),
    onSuccess: () => {
      message.success('Категория успешно создана');
      setIsModalOpen(false);
      form.resetFields();
      queryClient.invalidateQueries({ queryKey: ['categories'] });
    },
    onError: (err: any) => {
      message.error(err.response?.data?.message || 'Ошибка создания категории');
    },
  });

  const handleCreate = () => {
    form.validateFields().then((values) => {
      createMutation.mutate(values);
    });
  };

  // Настройки загрузки файлов
  const token = useAuthStore.getState().accessToken;
  const uploadProps: UploadProps = {
    name: 'file',
    action: 'http://localhost:8080/api/v1/admin/categories/upload',
    headers: {
      Authorization: `Bearer ${token}`,
    },
    showUploadList: false,
    onChange(info) {
      if (info.file.status === 'uploading') {
        // можно добавить лоадер если нужно
      }
      if (info.file.status === 'done') {
        const responseData = info.file.response?.data;
        const skipped = responseData?.skipped || 0;
        const skippedNames = responseData?.skippedCategories?.join(', ');

        if (skipped > 0) {
          Modal.warning({
            title: 'Импорт завершен с пропусками',
            content: (
              <>
                <p><strong>Добавлено:</strong> {responseData?.added}</p>
                <p><strong>Пропущено (уже существуют):</strong> {skipped}</p>
                <div style={{ maxHeight: '150px', overflowY: 'auto', background: '#f5f5f5', padding: '10px', marginTop: '10px' }}>
                  <Text type="secondary">{skippedNames}</Text>
                </div>
              </>
            ),
          });
        } else {
          message.success(`Загружено ${responseData?.added || 0} категорий`);
        }
        
        queryClient.invalidateQueries({ queryKey: ['categories'] });
      } else if (info.file.status === 'error') {
        message.error(`Ошибка загрузки: ${info.file.response?.message || info.file.error?.message}`);
      }
    },
  };

  const columns: ColumnsType<Category> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Название (RU)',
      dataIndex: 'name',
      render: (text) => <strong>{text}</strong>,
    },
    { title: 'Название (UZ)', dataIndex: 'nameUz' },
    {
      title: 'Slug',
      dataIndex: 'slug',
      render: (text) => <Tag>{text}</Tag>,
    },
    {
      title: 'Иконка',
      dataIndex: 'iconUrl',
      render: (url) => url ? (
        <img src={url} alt="icon" style={{ width: 24, height: 24, objectFit: 'contain' }} />
      ) : (
        <Text type="secondary">Нет картинки</Text>
      ),
    },
    { title: 'Сортировка', dataIndex: 'sortOrder', width: 100 },
    {
      title: 'Действия',
      render: (_: unknown, _record: Category) => (
        <Space>
           {/* Кнопки редактирования пока заглушки */}
          <Button type="link" size="small">Изменить иконку</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Управление Категориями</Title>
        <Space>
          <Button icon={<ReloadOutlined />} onClick={() => refetch()} loading={isFetching}>Обновить</Button>
          
          <Upload {...uploadProps}>
            <Button icon={<UploadOutlined />}>Импорт из Excel</Button>
          </Upload>

          <Button type="primary" icon={<PlusOutlined />} onClick={() => setIsModalOpen(true)}>
            Добавить вручную
          </Button>
        </Space>
      </div>

      <Table
        columns={columns}
        dataSource={data}
        loading={isLoading}
        rowKey="id"
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title="Новая категория"
        open={isModalOpen}
        onOk={handleCreate}
        onCancel={() => { setIsModalOpen(false); form.resetFields(); }}
        confirmLoading={createMutation.isPending}
        okText="Создать"
        cancelText="Отмена"
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="Название (RU)" rules={[{ required: true }]}>
            <Input placeholder="Например: Салоны красоты" />
          </Form.Item>
          <Form.Item name="nameUz" label="Название (UZ)">
            <Input placeholder="Например: Go'zallik salonlari" />
          </Form.Item>
          {/* slug генерируется автоматически на бэкенде если не передан, так что не делаем его обязательным тут */}
          <Form.Item name="sortOrder" label="Порядок сортировки" initialValue={0}>
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
