import { useState } from 'react';
import {
  Alert,
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd';
import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  UploadOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { UploadProps } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useAuthStore } from '../../store/authStore';
import {
  createCategory,
  deleteCategory,
  getAdminCategories,
  updateCategory,
  type AdminCategory,
  type CategoryPayload,
} from './categoriesApi';

const API_BASE_URL = import.meta.env.VITE_API_URL || '';
const { Title, Text } = Typography;
const ADMIN_CATEGORIES_QUERY = ['admin-categories'] as const;

function errorMessage(error: unknown, fallback: string) {
  const apiError = error as { response?: { data?: { message?: string } } };
  return apiError.response?.data?.message || fallback;
}

export const CategoriesPage = () => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCategory, setEditingCategory] = useState<AdminCategory | null>(null);
  const [form] = Form.useForm<CategoryPayload>();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const token = useAuthStore.getState().accessToken;

  const categoriesQuery = useQuery({
    queryKey: ADMIN_CATEGORIES_QUERY,
    queryFn: getAdminCategories,
  });

  const refreshCategories = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ADMIN_CATEGORIES_QUERY }),
      queryClient.invalidateQueries({ queryKey: ['categories'] }),
    ]);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    setEditingCategory(null);
    form.resetFields();
  };

  const createMutation = useMutation({
    mutationFn: createCategory,
    onSuccess: async () => {
      message.success('Категория успешно создана');
      closeModal();
      await refreshCategories();
    },
    onError: (error) => {
      message.error(errorMessage(error, 'Ошибка создания категории'));
    },
  });

  const updateMutation = useMutation({
    mutationFn: ({ id, payload }: { id: number; payload: CategoryPayload }) =>
      updateCategory(id, payload),
    onSuccess: async () => {
      message.success('Категория успешно обновлена');
      closeModal();
      await refreshCategories();
    },
    onError: (error) => {
      message.error(errorMessage(error, 'Ошибка обновления категории'));
    },
  });

  const deleteMutation = useMutation({
    mutationFn: deleteCategory,
    onSuccess: async () => {
      message.success('Категория удалена');
      await refreshCategories();
    },
    onError: (error) => {
      message.error(errorMessage(error, 'Ошибка удаления категории'));
    },
  });

  const openCreate = () => {
    setEditingCategory(null);
    form.setFieldsValue({
      name: '',
      nameUz: '',
      slug: '',
      iconUrl: '',
      sortOrder: 0,
      active: true,
    });
    setIsModalOpen(true);
  };

  const openEdit = (category: AdminCategory) => {
    setEditingCategory(category);
    form.setFieldsValue({
      name: category.name,
      nameUz: category.nameUz || '',
      slug: category.slug,
      iconUrl: category.iconUrl || '',
      sortOrder: category.sortOrder,
      active: category.active,
    });
    setIsModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: CategoryPayload = {
      ...values,
      nameUz: values.nameUz || '',
      slug: values.slug || '',
      iconUrl: values.iconUrl || '',
      sortOrder: values.sortOrder ?? 0,
      active: values.active ?? true,
    };

    if (editingCategory) {
      updateMutation.mutate({ id: editingCategory.id, payload });
    } else {
      createMutation.mutate(payload);
    }
  };

  const authHeaders: Record<string, string> = {};
  if (token) {
    authHeaders.Authorization = `Bearer ${token}`;
  }
  const excelUploadProps: UploadProps = {
    name: 'file',
    action: `${API_BASE_URL}/api/v1/admin/categories/upload`,
    headers: authHeaders,
    accept: '.xlsx',
    showUploadList: false,
    onChange(info) {
      if (info.file.status === 'done') {
        const result = info.file.response?.data;
        const skipped = result?.skipped || 0;
        if (skipped > 0) {
          modal.warning({
            title: 'Импорт завершён с пропусками',
            content: (
              <>
                <p><strong>Добавлено:</strong> {result?.added || 0}</p>
                <p><strong>Пропущено:</strong> {skipped}</p>
                <Text type="secondary">
                  {result?.skippedCategories?.join(', ') || 'Названия не переданы'}
                </Text>
              </>
            ),
          });
        } else {
          message.success(`Загружено ${result?.added || 0} категорий`);
        }
        void refreshCategories();
      } else if (info.file.status === 'error') {
        message.error(info.file.response?.message || 'Ошибка импорта категорий');
      }
    },
  };

  const iconUploadProps: UploadProps = {
    name: 'file',
    action: `${API_BASE_URL}/api/v1/media/upload`,
    headers: authHeaders,
    accept: 'image/*',
    maxCount: 1,
    showUploadList: false,
    onChange(info) {
      if (info.file.status === 'done') {
        const iconUrl = info.file.response?.data?.url;
        if (iconUrl) {
          form.setFieldValue('iconUrl', iconUrl);
          message.success('Иконка загружена');
        } else {
          message.error('Сервис не вернул URL иконки');
        }
      } else if (info.file.status === 'error') {
        message.error('Ошибка загрузки иконки');
      }
    },
  };

  const columns: ColumnsType<AdminCategory> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    {
      title: 'Название (RU)',
      dataIndex: 'name',
      render: (text: string) => <strong>{text}</strong>,
    },
    {
      title: 'Название (UZ)',
      dataIndex: 'nameUz',
      render: (text: string | null) => text || <Text type="secondary">Не задано</Text>,
    },
    {
      title: 'Slug',
      dataIndex: 'slug',
      render: (text: string) => <Tag>{text}</Tag>,
    },
    {
      title: 'Иконка',
      dataIndex: 'iconUrl',
      render: (url: string | null) => url ? (
        <img src={url} alt="Иконка категории" style={{ width: 24, height: 24, objectFit: 'contain' }} />
      ) : (
        <Text type="secondary">Нет картинки</Text>
      ),
    },
    { title: 'Сортировка', dataIndex: 'sortOrder', width: 100 },
    {
      title: 'Статус',
      dataIndex: 'active',
      width: 110,
      render: (active: boolean) => (
        <Tag color={active ? 'green' : 'default'}>{active ? 'Активна' : 'Выключена'}</Tag>
      ),
    },
    {
      title: 'Действия',
      width: 220,
      render: (_, category) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<EditOutlined />}
            aria-label="Редактировать"
            onClick={() => openEdit(category)}
          >
            Редактировать
          </Button>
          <Popconfirm
            title="Удалить категорию?"
            description="Удаление возможно, только если категория не используется купонами."
            okText="Да, удалить"
            cancelText="Отмена"
            okButtonProps={{ danger: true, loading: deleteMutation.isPending }}
            onConfirm={() => deleteMutation.mutate(category.id)}
          >
            <Button
              type="link"
              danger
              size="small"
              icon={<DeleteOutlined />}
              aria-label="Удалить"
            >
              Удалить
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Управление категориями</Title>
        <Space wrap>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => categoriesQuery.refetch()}
            loading={categoriesQuery.isFetching}
          >
            Обновить
          </Button>
          <Upload {...excelUploadProps}>
            <Button icon={<UploadOutlined />}>Импорт из Excel</Button>
          </Upload>
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            Добавить вручную
          </Button>
        </Space>
      </div>

      {categoriesQuery.isError && (
        <Alert
          type="error"
          showIcon
          title="Не удалось загрузить категории"
          action={<Button onClick={() => categoriesQuery.refetch()}>Повторить</Button>}
          style={{ marginBottom: 16 }}
        />
      )}

      <Table
        columns={columns}
        dataSource={categoriesQuery.data || []}
        loading={categoriesQuery.isLoading}
        rowKey="id"
        locale={{ emptyText: 'Категорий пока нет' }}
        pagination={{ pageSize: 20 }}
      />

      <Modal
        title={editingCategory ? 'Редактирование категории' : 'Новая категория'}
        open={isModalOpen}
        onOk={() => void handleSave()}
        onCancel={closeModal}
        confirmLoading={createMutation.isPending || updateMutation.isPending}
        okText={editingCategory ? 'Сохранить' : 'Создать'}
        cancelText="Отмена"
        destroyOnHidden
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="name"
            label="Название (RU)"
            rules={[{ required: true, whitespace: true, message: 'Введите название' }]}
          >
            <Input maxLength={100} placeholder="Например: Салоны красоты" />
          </Form.Item>
          <Form.Item name="nameUz" label="Название (UZ)">
            <Input maxLength={100} placeholder="Например: Go'zallik salonlari" />
          </Form.Item>
          <Form.Item
            name="slug"
            label="Slug"
            extra="Если оставить пустым, slug будет создан из русского названия."
          >
            <Input maxLength={100} placeholder="beauty-salons" />
          </Form.Item>
          <Form.Item name="iconUrl" label="URL иконки">
            <Input maxLength={500} placeholder="/api/v1/media/..." />
          </Form.Item>
          <Form.Item label="Загрузка иконки">
            <Upload {...iconUploadProps}>
              <Button icon={<UploadOutlined />} aria-label="Загрузить иконку">
                Загрузить иконку
              </Button>
            </Upload>
          </Form.Item>
          <Form.Item name="sortOrder" label="Порядок сортировки">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="active" label="Активна" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
