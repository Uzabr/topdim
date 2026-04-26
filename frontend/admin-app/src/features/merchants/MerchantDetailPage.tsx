import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Descriptions, Tag, Button, Typography, Space, Card, Table, Tabs, App,
  Modal, Form, Input, Switch, Divider, Spin, Alert,
} from 'antd';
import {
  ArrowLeftOutlined, EditOutlined, PlusOutlined,
  CheckCircleOutlined, CloseCircleOutlined, ExclamationCircleOutlined,
} from '@ant-design/icons';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchMerchantDetail, updateMerchant, setMerchantActive, fetchMerchantCoupons } from './api';
import type { ColumnsType } from 'antd/es/table';

const { Title, Text } = Typography;

export const MerchantDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const merchantId = Number(id);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();

  const [editOpen, setEditOpen] = useState(false);
  const [editForm] = Form.useForm();
  const [couponPage, setCouponPage] = useState(0);

  const { data: merchant, isLoading } = useQuery({
    queryKey: ['admin-merchant', merchantId],
    queryFn: () => fetchMerchantDetail(merchantId),
    enabled: !!merchantId,
  });

  const { data: couponsData, isLoading: couponsLoading } = useQuery({
    queryKey: ['admin-merchant-coupons', merchantId, couponPage],
    queryFn: () => fetchMerchantCoupons(merchantId, { page: couponPage, size: 10 }),
    enabled: !!merchantId,
  });

  const activeMutation = useMutation({
    mutationFn: (active: boolean) => setMerchantActive(merchantId, active),
    onSuccess: () => {
      message.success('Статус мерчанта обновлён');
      queryClient.invalidateQueries({ queryKey: ['admin-merchant', merchantId] });
      queryClient.invalidateQueries({ queryKey: ['admin-merchants'] });
    },
    onError: (err: any) => {
      const msg = err?.response?.data?.message || err.message || 'Ошибка';
      message.error(msg);
    },
  });

  const updateMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) => updateMerchant(merchantId, values),
    onSuccess: () => {
      message.success('Мерчант обновлён');
      setEditOpen(false);
      queryClient.invalidateQueries({ queryKey: ['admin-merchant', merchantId] });
      queryClient.invalidateQueries({ queryKey: ['admin-merchants'] });
    },
    onError: () => message.error('Ошибка при обновлении'),
  });

  const handleToggleActive = () => {
    if (!merchant) return;
    const newActive = !merchant.active;
    if (!newActive) {
      modal.confirm({
        title: 'Деактивация мерчанта',
        icon: <ExclamationCircleOutlined />,
        content: 'Вы уверены? Деактивация будет заблокирована, если у мерчанта есть активные купоны.',
        okText: 'Деактивировать',
        okButtonProps: { danger: true },
        onOk: () => activeMutation.mutate(false),
      });
    } else {
      activeMutation.mutate(true);
    }
  };

  const openEdit = () => {
    if (!merchant) return;
    editForm.setFieldsValue({
      name: merchant.name,
      description: merchant.description,
      logoUrl: merchant.logoUrl,
      coverUrl: merchant.coverUrl,
      email: merchant.email,
      website: merchant.website,
      contactPerson: merchant.contactPerson,
      locations: merchant.locations.map((l) => ({
        title: l.title,
        address: l.address,
        phone: l.phone,
        workingHours: l.workingHours,
        primary: l.primary,
      })),
    });
    setEditOpen(true);
  };

  const couponColumns: ColumnsType<Record<string, any>> = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: 'Название', dataIndex: 'title', ellipsis: true },
    {
      title: 'Статус',
      dataIndex: 'status',
      width: 120,
      render: (s: string) => {
        const colors: Record<string, string> = {
          ACTIVE: 'green', DRAFT: 'default', WAITING_FOR_MERCHANT: 'orange',
          ARCHIVED: 'red', SOLD_OUT: 'blue', LEAD: 'purple',
        };
        return <Tag color={colors[s] || 'default'}>{s}</Tag>;
      },
    },
    { title: 'Продано', dataIndex: 'totalSold', width: 90 },
    { title: 'Погашено', dataIndex: 'redeemedCount', width: 100 },
    {
      title: 'Действия',
      width: 100,
      render: (_, record: any) => (
        <Button type="link" size="small" onClick={() => navigate(`/moderation/coupons/edit/${record.id}`)}>
          Открыть
        </Button>
      ),
    },
  ];

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '80px auto' }} />;
  if (!merchant) return <Alert type="error" message="Мерчант не найден" />;

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/catalog/merchants')}>
          Назад
        </Button>
      </Space>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 16 }}>
        <Title level={4} style={{ margin: 0 }}>{merchant.name}</Title>
        <Space>
          <Button icon={<EditOutlined />} onClick={openEdit}>Редактировать</Button>
          <Button
            type={merchant.active ? 'default' : 'primary'}
            danger={merchant.active}
            onClick={handleToggleActive}
            loading={activeMutation.isPending}
          >
            {merchant.active ? 'Деактивировать' : 'Активировать'}
          </Button>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => navigate(`/moderation/coupons/create?merchantId=${merchant.id}`)}
          >
            Создать купон
          </Button>
        </Space>
      </div>

      {!merchant.publicationReady && (
        <Alert
          type="warning"
          showIcon
          message="Не готов к публикации"
          description={merchant.publicationBlockReason}
          style={{ marginBottom: 16 }}
        />
      )}

      <Tabs
        defaultActiveKey="info"
        items={[
          {
            key: 'info',
            label: 'Информация',
            children: (
              <Space direction="vertical" size={16} style={{ width: '100%' }}>
                <Card size="small" title="Профиль">
                  <Descriptions column={2} size="small" bordered>
                    <Descriptions.Item label="ID">{merchant.id}</Descriptions.Item>
                    <Descriptions.Item label="User ID">{merchant.userId || '—'}</Descriptions.Item>
                    <Descriptions.Item label="Email">{merchant.email || '—'}</Descriptions.Item>
                    <Descriptions.Item label="Сайт">{merchant.website || '—'}</Descriptions.Item>
                    <Descriptions.Item label="Контактное лицо">{merchant.contactPerson || '—'}</Descriptions.Item>
                    <Descriptions.Item label="Статус">
                      {merchant.active ? (
                        <Tag icon={<CheckCircleOutlined />} color="success">Активен</Tag>
                      ) : (
                        <Tag icon={<CloseCircleOutlined />} color="default">Не активен</Tag>
                      )}
                    </Descriptions.Item>
                    <Descriptions.Item label="Описание" span={2}>{merchant.description || '—'}</Descriptions.Item>
                  </Descriptions>
                </Card>

                <Card size="small" title="Локации">
                  {merchant.locations.length === 0 ? (
                    <Text type="secondary">Нет локаций</Text>
                  ) : (
                    merchant.locations.map((loc) => (
                      <Card key={loc.id} size="small" style={{ marginBottom: 8 }}
                        title={<Space>{loc.title || 'Без названия'}{loc.primary && <Tag color="blue">Primary</Tag>}</Space>}
                      >
                        <Descriptions column={2} size="small">
                          <Descriptions.Item label="Адрес">{loc.address || '—'}</Descriptions.Item>
                          <Descriptions.Item label="Телефон">{loc.phone || '—'}</Descriptions.Item>
                          <Descriptions.Item label="Часы работы">{loc.workingHours || '—'}</Descriptions.Item>
                          <Descriptions.Item label="Статус">
                            {loc.active ? <Tag color="green">Активна</Tag> : <Tag>Не активна</Tag>}
                          </Descriptions.Item>
                        </Descriptions>
                      </Card>
                    ))
                  )}
                </Card>
              </Space>
            ),
          },
          {
            key: 'coupons',
            label: `Купоны (${couponsData?.totalElements ?? 0})`,
            children: (
              <Table
                columns={couponColumns}
                dataSource={couponsData?.content}
                loading={couponsLoading}
                rowKey="id"
                pagination={{
                  current: couponPage + 1,
                  pageSize: 10,
                  total: couponsData?.totalElements,
                  onChange: (p) => setCouponPage(p - 1),
                  showTotal: (t) => `Всего: ${t}`,
                }}
                size="small"
              />
            ),
          },
        ]}
      />

      {/* Edit Modal */}
      <Modal
        open={editOpen}
        title="Редактирование мерчанта"
        onCancel={() => setEditOpen(false)}
        onOk={() => editForm.submit()}
        confirmLoading={updateMutation.isPending}
        okText="Сохранить"
        cancelText="Отмена"
        width={640}
      >
        <Form
          form={editForm}
          layout="vertical"
          onFinish={(values) => updateMutation.mutate(values)}
        >
          <Form.Item name="name" label="Название" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="description" label="Описание">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item name="contactPerson" label="Контактное лицо">
            <Input />
          </Form.Item>
          <Form.Item name="email" label="Email">
            <Input />
          </Form.Item>
          <Form.Item name="website" label="Сайт">
            <Input />
          </Form.Item>
          <Form.Item name="logoUrl" label="Logo URL">
            <Input />
          </Form.Item>
          <Form.Item name="coverUrl" label="Cover URL">
            <Input />
          </Form.Item>

          <Divider>Локации</Divider>
          <Form.List name="locations">
            {(fields, { add, remove }) => (
              <>
                {fields.map(({ key, name, ...restField }) => (
                  <Card key={key} size="small" style={{ marginBottom: 8 }}
                    extra={<Button danger size="small" onClick={() => remove(name)}>Удалить</Button>}
                    title={`Локация ${name + 1}`}
                  >
                    <Form.Item {...restField} name={[name, 'title']} label="Название">
                      <Input />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'address']} label="Адрес" rules={[{ required: true }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'phone']} label="Телефон" rules={[{ required: true }]}>
                      <Input />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'workingHours']} label="Часы работы">
                      <Input />
                    </Form.Item>
                    <Form.Item {...restField} name={[name, 'primary']} label="Primary" valuePropName="checked">
                      <Switch />
                    </Form.Item>
                  </Card>
                ))}
                <Button type="dashed" block onClick={() => add({ primary: false })} icon={<PlusOutlined />}>
                  Добавить локацию
                </Button>
              </>
            )}
          </Form.List>
        </Form>
      </Modal>
    </div>
  );
};
