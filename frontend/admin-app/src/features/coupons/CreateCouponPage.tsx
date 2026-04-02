import { useState } from 'react';
import { Card, Form, Input, InputNumber, Button, Typography, App, Row, Col, DatePicker, Select, Tag, Modal, Space, Divider } from 'antd';
import { ExclamationCircleOutlined, PlusOutlined, MinusCircleOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import api from '../../api/client';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface CreateCouponFormData {
  title: string;
  categoryId: number;
  merchantId?: number;
  coverImageUrl: string;
  shortDescription: string;
  fullDescription: string;
  oldPrice: number;
  fromPrice: number;
  discountPercent: number;
  buyUntil: dayjs.Dayjs;
  useUntil: dayjs.Dayjs;
  address: string;
  contactPhone: string;
  workingHours: string;
  options: {
    title: string;
    regularPrice: number;
    couponPrice: number;
    quantityLimit?: number;
  }[];
}

export const CreateCouponPage = () => {
  const [form] = Form.useForm<CreateCouponFormData>();
  const [merchantForm] = Form.useForm();
  const [isMerchantModalOpen, setIsMerchantModalOpen] = useState(false);
  
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();

  // Загружаем категории
  const { data: categories, isLoading: isCategoriesLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      const res = await api.get('/api/v1/categories'); 
      return res.data.data;
    }
  });

  // Загружаем мерчантов
  const { data: merchants, isLoading: isMerchantsLoading } = useQuery({
    queryKey: ['merchants-list'],
    queryFn: async () => {
      const res = await api.get('/api/v1/admin/merchants');
      return res.data.data;
    }
  });

  // Быстрое создание мерчанта (только название)
  const createMerchantMutation = useMutation({
    mutationFn: async (values: { name: string }) => {
      const { data } = await api.post('/api/v1/admin/merchants', values);
      return data.data;
    },
    onSuccess: (newMerchant) => {
      message.success(`Партнер "${newMerchant.name}" добавлен`);
      queryClient.invalidateQueries({ queryKey: ['merchants-list'] });
      form.setFieldValue('merchantId', newMerchant.id);
      setIsMerchantModalOpen(false);
      merchantForm.resetFields();
    },
    onError: () => {
      message.error('Ошибка создания партнера');
    }
  });

  const createMutation = useMutation({
    mutationFn: async (values: CreateCouponFormData) => {
      const payload = {
        ...values,
        buyUntil: values.buyUntil.format('YYYY-MM-DDTHH:mm:ss'),
        useUntil: values.useUntil.format('YYYY-MM-DDTHH:mm:ss'),
      };
      const { data } = await api.post('/api/v1/admin/coupons', payload);
      return data;
    },
    onSuccess: () => {
      message.success('Купон успешно создан и опубликован!');
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
      navigate('/moderation/coupons');
    },
    onError: (err: any) => {
      message.error(err.response?.data?.message || 'Ошибка создания купона');
    }
  });

  const onFinish = (values: CreateCouponFormData) => {
    modal.confirm({
      title: 'Подтверждение публикации',
      icon: <ExclamationCircleOutlined />,
      content: (
        <div>
          <p>Внимательно проверьте все данные. У купона должна быть картинка и правильные цены (Варианты).</p>
          <Text type="danger">Публикуем купон?</Text>
        </div>
      ),
      okText: 'Да, опубликовать',
      cancelText: 'Отмена',
      onOk: () => {
        createMutation.mutate(values);
      },
    });
  };

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Создать купон</Title>
        <Tag color="green">Публикуется активно</Tag>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ discountPercent: 0, oldPrice: 0, options: [{}] }}
        >
          <Row gutter={24}>
            {/* Левая колонка */}
            <Col xs={24} md={16}>
              <Form.Item
                name="title"
                label="Название услуги/акции"
                rules={[{ required: true, message: 'Введите название' }]}
              >
                <Input placeholder="Например: Скидка 50% на все сеты роллов" size="large" />
              </Form.Item>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item
                    name="merchantId"
                    label="Партнер (Мерчант)"
                  >
                    <Space.Compact style={{ width: '100%' }}>
                      <Select 
                        placeholder="Выберите партнера или оставьте пустым" 
                        loading={isMerchantsLoading}
                        allowClear
                        showSearch
                        optionFilterProp="children"
                        style={{ width: 'calc(100% - 40px)' }}
                      >
                        {merchants?.map((m: any) => (
                          <Select.Option key={m.id} value={m.id}>{m.name}</Select.Option>
                        ))}
                      </Select>
                      <Button icon={<PlusOutlined />} onClick={() => setIsMerchantModalOpen(true)} title="Добавить нового партнера" />
                    </Space.Compact>
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="categoryId"
                    label="Категория"
                    rules={[{ required: true, message: 'Выберите категорию' }]}
                  >
                    <Select placeholder="Выберите категорию" loading={isCategoriesLoading} showSearch optionFilterProp="children">
                      {categories?.map((c: any) => (
                        <Select.Option key={c.id} value={c.id}>{c.name}</Select.Option>
                      ))}
                    </Select>
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={8}>
                  <Form.Item name="oldPrice" label="Старая цена (сум) к примеру">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item
                    name="fromPrice"
                    label="Новая цена от (сум)"
                    rules={[{ required: true, message: 'Обязательное поле' }]}
                  >
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="discountPercent" label="Процент скидки (%)">
                    <InputNumber min={0} max={100} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                name="coverImageUrl"
                label="URL картинки (Обязательно)"
                rules={[{ required: true, message: 'Добавьте изображение для карточки товара' }]}
              >
                <Input placeholder="https://example.com/image.jpg" />
              </Form.Item>

              <Form.Item name="shortDescription" label="Краткое описание">
                <TextArea rows={2} placeholder="Пара слов об акции..." />
              </Form.Item>

              <Form.Item name="fullDescription" label="Полное описание">
                <TextArea rows={4} placeholder="Подробное описание услуг и преимуществ..." />
              </Form.Item>

              {/* Варианты купонов (Options) */}
              <Card type="inner" title="Варианты покупки (Цены)" style={{ marginBottom: 24 }}>
                <Form.List name="options" rules={[
                    {
                      validator: async (_, options) => {
                        if (!options || options.length < 1) {
                          return Promise.reject(new Error('Добавьте хотя бы один вариант покупки'));
                        }
                      },
                    },
                  ]}>
                  {(fields, { add, remove }, { errors }) => (
                    <>
                      {fields.map(({ key, name, ...restField }) => (
                        <div key={key} style={{ display: 'flex', gap: 16, alignItems: 'flex-start', marginBottom: 16 }}>
                          <Form.Item
                            {...restField}
                            name={[name, 'title']}
                            rules={[{ required: true, message: 'Название обязательно' }]}
                            style={{ flex: 2, marginBottom: 0 }}
                          >
                            <Input placeholder="Название (напр. Сет №1)" />
                          </Form.Item>
                          <Form.Item
                            {...restField}
                            name={[name, 'regularPrice']}
                            rules={[{ required: true, message: 'Обычная цена' }]}
                            style={{ flex: 1, marginBottom: 0 }}
                          >
                            <InputNumber placeholder="Обычная цена" style={{ width: '100%' }} />
                          </Form.Item>
                          <Form.Item
                            {...restField}
                            name={[name, 'couponPrice']}
                            rules={[{ required: true, message: 'Цена со скидкой' }]}
                            style={{ flex: 1, marginBottom: 0 }}
                          >
                            <InputNumber placeholder="Цена со скидкой" style={{ width: '100%' }} />
                          </Form.Item>
                          <Form.Item
                            {...restField}
                            name={[name, 'quantityLimit']}
                            style={{ flex: 1, marginBottom: 0 }}
                          >
                            <InputNumber placeholder="Лимит (шт)" style={{ width: '100%' }} />
                          </Form.Item>
                          {fields.length > 1 && (
                            <MinusCircleOutlined onClick={() => remove(name)} style={{ marginTop: 10, color: 'red' }} />
                          )}
                        </div>
                      ))}
                      <Form.Item style={{ marginBottom: 0 }}>
                        <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                          Добавить вариант покупки
                        </Button>
                        <Form.ErrorList errors={errors} />
                      </Form.Item>
                    </>
                  )}
                </Form.List>
              </Card>
            </Col>

            {/* Правая колонка (Сайдбар) */}
            <Col xs={24} md={8}>
              <Card type="inner" title="Сроки проведения" style={{ marginBottom: 16 }}>
                <Form.Item
                  name="buyUntil"
                  label="Можно купить купон до"
                  rules={[{ required: true, message: 'Укажите дату' }]}
                >
                  <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item
                  name="useUntil"
                  label="Можно использовать купон до"
                  rules={[{ required: true, message: 'Укажите дату' }]}
                >
                  <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
                </Form.Item>
              </Card>

              <Card type="inner" title="Контакты заведения">
                <Form.Item name="address" label="Адрес проведения">
                  <Input placeholder="г. Ташкент, ул. Амира Темура" />
                </Form.Item>
                <Form.Item name="contactPhone" label="Контактный телефон">
                  <Input placeholder="+998 90 000 00 00" />
                </Form.Item>
                <Form.Item name="workingHours" label="Часы работы">
                  <Input placeholder="Пн-Вс: 09:00 - 22:00" />
                </Form.Item>
              </Card>

              <Form.Item>
                <Button 
                  type="primary" 
                  htmlType="submit" 
                  size="large" 
                  block 
                  loading={createMutation.isPending}
                  style={{ marginTop: 24 }}
                >
                  Опубликовать купон
                </Button>
              </Form.Item>
            </Col>
          </Row>
        </Form>
      </Card>

      {/* Модалка быстрого создания партнера */}
      <Modal
        title="Быстрое создание партнера"
        open={isMerchantModalOpen}
        onCancel={() => setIsMerchantModalOpen(false)}
        onOk={() => {
          merchantForm.validateFields().then(values => {
            createMerchantMutation.mutate(values);
          });
        }}
        confirmLoading={createMerchantMutation.isPending}
        okText="Создать и выбрать"
      >
        <Form form={merchantForm} layout="vertical">
          <Form.Item name="name" label="Название организации" rules={[{ required: true }]}>
            <Input placeholder="Например: PizzaLab" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};
