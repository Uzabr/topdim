import { useState } from 'react';
import { Card, Form, Input, InputNumber, Button, Typography, App, Row, Col, DatePicker, Select, Tag, Modal, Space, Upload } from 'antd';
import { ExclamationCircleOutlined, PlusOutlined, UploadOutlined, MinusCircleOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import api from '../../api/client';
import { useAuthStore } from '../../store/authStore';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface CreateCouponOptionData {
  title: string;
  regularPrice: number;
  couponPrice: number;
  quantityLimit?: number;
}

interface CreateCouponFormData {
  title: string;
  categoryId: number;
  merchantId?: number;
  coverImageUrl: string;
  shortDescription: string;
  fullDescription: string;
  terms: string;
  usageRules: string;
  howToUse: string;
  oldPrice: number;
  fromPrice: number;
  discountPercent: number;
  options: CreateCouponOptionData[];
  buyUntil: dayjs.Dayjs;
  useUntil: dayjs.Dayjs;
  address: string;
  contactPhone: string;
  workingHours: string;
}

export const CreateCouponPage = () => {
  const [form] = Form.useForm<CreateCouponFormData>();
  const [merchantForm] = Form.useForm();
  const [isMerchantModalOpen, setIsMerchantModalOpen] = useState(false);
  const [coverImageUrl, setCoverImageUrl] = useState<string>('');
  
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
          <p>Внимательно проверьте все данные. У купона должна быть картинка и правильные цены.</p>
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
          initialValues={{ discountPercent: 0, oldPrice: 0, options: [] }}
        >
          <Row gutter={24}>
            {/* Левая колонка */}
            <Col xs={24} md={16}>
              <Form.Item
                name="title"
                label="Название услуги/акции"
                rules={[{ required: true, message: 'Введите название' }]}
                extra="Отображается крупным шрифтом в карточке купона (на Главной странице и в Каталоге)."
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
                    extra="Определяет раздел в клиентском каталоге."
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
                  <Form.Item name="oldPrice" label="Старая цена (сум)" tooltip="Цена без скидки (будет эффектно перечеркнута в карточке)">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item
                    name="fromPrice"
                    label="Новая цена (сум)"
                    rules={[{ required: true, message: 'Обязательное поле' }]}
                    tooltip="Текущая финальная цена (жирным шрифтом)"
                  >
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={8}>
                  <Form.Item name="discountPercent" label="Процент скидки (%)" tooltip="Выводится в виде яркого красного бейджа, например '-50%'">
                    <InputNumber min={0} max={100} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item
                name="coverImageUrl"
                label="Изображение (Миниатюра купона)"
                rules={[{ required: true, message: 'Добавьте изображение для карточки товара' }]}
              >
                <Upload
                  name="file"
                  action="http://localhost:8080/api/v1/media/upload"
                  headers={{ Authorization: `Bearer ${useAuthStore.getState().accessToken}` }}
                  listType="picture-card"
                  maxCount={1}
                  showUploadList={false}
                  onChange={(info) => {
                    if (info.file.status === 'done') {
                      const urlPath = info.file.response?.data?.url;
                      const fullUrl = `http://localhost:8080${urlPath}`;
                      setCoverImageUrl(fullUrl);
                      form.setFieldValue('coverImageUrl', fullUrl);
                      message.success('Изображение успешно загружено!');
                    } else if (info.file.status === 'error') {
                      message.error('Ошибка загрузки изображения');
                    }
                  }}
                >
                  {coverImageUrl ? (
                    <img src={coverImageUrl} alt="cover" style={{ width: '100%', maxHeight: '100px', objectFit: 'contain' }} />
                  ) : (
                    <div>
                      <UploadOutlined />
                      <div style={{ marginTop: 8 }}>Загрузить (Max 5MB)</div>
                    </div>
                  )}
                </Upload>
              </Form.Item>

              <Form.Item name="shortDescription" label="Краткое описание" extra="Пара слов об акции. Отображается прямо на плитке купона в общей ленте.">
                <TextArea rows={2} placeholder="Пара слов об акции..." />
              </Form.Item>

              <Form.Item name="fullDescription" label="Полное описание" extra="Подробное описание услуг и преимуществ. Видно только когда пользователь кликнет по купону.">
                <TextArea rows={4} placeholder="Подробное описание услуг и преимуществ..." />
              </Form.Item>

              <Card type="inner" title="Варианты покупки (Виды сертификатов)" style={{ marginBottom: 24, marginTop: 16 }}>
                <Form.List name="options">
                  {(fields, { add, remove }) => (
                    <>
                      {fields.map(({ key, name, ...restField }) => (
                        <Row gutter={16} key={key} style={{ marginBottom: 16, borderBottom: '1px solid #f0f0f0', paddingBottom: 16 }}>
                          <Col span={8}>
                            <Form.Item
                              {...restField}
                              name={[name, 'title']}
                              label="Название опции"
                              rules={[{ required: true, message: 'Обязательно' }]}
                            >
                              <Input placeholder="Например: Сет для двоих" />
                            </Form.Item>
                          </Col>
                          <Col span={5}>
                            <Form.Item
                              {...restField}
                              name={[name, 'regularPrice']}
                              label="Обычная цена"
                              rules={[{ required: true, message: 'Обязательно' }]}
                            >
                              <InputNumber min={0} style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                          <Col span={5}>
                            <Form.Item
                              {...restField}
                              name={[name, 'couponPrice']}
                              label="Цена со скидкой"
                              rules={[{ required: true, message: 'Обязательно' }]}
                            >
                              <InputNumber min={0} style={{ width: '100%' }} />
                            </Form.Item>
                          </Col>
                          <Col span={4}>
                            <Form.Item
                              {...restField}
                              name={[name, 'quantityLimit']}
                              label="Лимит (шт)"
                            >
                              <InputNumber min={1} style={{ width: '100%' }} placeholder="∞" />
                            </Form.Item>
                          </Col>
                          <Col span={2} style={{ display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                            <MinusCircleOutlined onClick={() => remove(name)} style={{ color: 'red', fontSize: '20px', marginTop: '10px', cursor: 'pointer' }} />
                          </Col>
                        </Row>
                      ))}
                      <Form.Item style={{ margin: 0 }}>
                        <Button type="dashed" onClick={() => add()} block icon={<PlusOutlined />}>
                          Добавить вариант покупки
                        </Button>
                      </Form.Item>
                    </>
                  )}
                </Form.List>
              </Card>

              <Card type="inner" title="Правила и Условия" style={{ marginBottom: 24 }}>
                <Form.Item name="terms" label="Условия (ограничения)" extra="Возрастные ограничения, средний чек и прочая важная информация.">
                  <TextArea rows={2} placeholder="Например: Обслуживание 10% оплачивается отдельно." />
                </Form.Item>
                <Form.Item name="usageRules" label="Общие правила" extra="На что скидка не действует и с какими акциями суммируется.">
                  <TextArea rows={2} placeholder="Например: Скидка не действует на бар." />
                </Form.Item>
                <Form.Item name="howToUse" label="Как использовать (Инструкция)" extra="Пошаговое описание процесса активации.">
                  <TextArea rows={2} placeholder="1. Покажите купон... 2. Закажите..." />
                </Form.Item>
              </Card>
            </Col>

            {/* Правая колонка (Сайдбар) */}
            <Col xs={24} md={8}>
              <Card type="inner" title="Сроки проведения" style={{ marginBottom: 16 }}>
                <Form.Item
                  name="buyUntil"
                  label="Можно купить купон до"
                  rules={[{ required: true, message: 'Укажите дату' }]}
                  tooltip="С этой даты купон исчезнет из активных каталогов"
                >
                  <DatePicker showTime format="YYYY-MM-DD HH:mm" style={{ width: '100%' }} />
                </Form.Item>
                <Form.Item
                  name="useUntil"
                  label="Можно использовать купон до"
                  rules={[{ required: true, message: 'Укажите дату' }]}
                  tooltip="Крайний срок, когда клиент может прийти с этим купоном в заведение"
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
