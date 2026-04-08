import { useEffect, useState } from 'react';
import { Card, Form, Input, InputNumber, Button, Typography, App, Row, Col, DatePicker, Select, Tag, Modal, Space, Upload, Alert, Spin, Switch } from 'antd';
import { ExclamationCircleOutlined, PlusOutlined, UploadOutlined, MinusCircleOutlined, InfoCircleOutlined, ArrowLeftOutlined, DeleteOutlined, GiftOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { useNavigate, useParams } from 'react-router-dom';
import dayjs from 'dayjs';
import api from '../../api/client';
import { useAuthStore } from '../../store/authStore';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface CouponOptionData {
  title: string;
  regularPrice: number;
  couponPrice: number;
  quantityLimit?: number;
}

interface CouponFormData {
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
  options: CouponOptionData[];
  buyUntil: dayjs.Dayjs;
  useUntil: dayjs.Dayjs;
  address: string;
  contactPhone: string;
  workingHours: string;
  giftAvailable: boolean;
  images: string[];
}

export const CouponFormPage = () => {
  const { id } = useParams<{ id: string }>();
  const isEditMode = !!id;

  const [form] = Form.useForm<CouponFormData>();
  const [merchantForm] = Form.useForm();
  const [isMerchantModalOpen, setIsMerchantModalOpen] = useState(false);
  const [coverImageUrl, setCoverImageUrl] = useState<string>('');
  const [galleryImages, setGalleryImages] = useState<string[]>([]);
  
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();

  // Загружаем существующий купон (только в режиме редактирования)
  const { data: existingCoupon, isLoading: isCouponLoading } = useQuery({
    queryKey: ['admin-coupon', id],
    queryFn: async () => {
      const res = await api.get(`/api/v1/admin/coupons/${id}`);
      return res.data.data;
    },
    enabled: isEditMode,
  });

  // Заполняем форму старыми данными
  useEffect(() => {
    if (existingCoupon) {
      form.setFieldsValue({
        title: existingCoupon.title,
        categoryId: existingCoupon.category?.id,
        merchantId: existingCoupon.merchant?.id,
        coverImageUrl: existingCoupon.coverImageUrl,
        shortDescription: existingCoupon.shortDescription,
        fullDescription: existingCoupon.fullDescription,
        terms: existingCoupon.terms,
        usageRules: existingCoupon.usageRules,
        howToUse: existingCoupon.howToUse,
        oldPrice: existingCoupon.oldPrice,
        fromPrice: existingCoupon.fromPrice,
        discountPercent: existingCoupon.discountPercent,
        buyUntil: existingCoupon.buyUntil ? dayjs(existingCoupon.buyUntil) : undefined,
        useUntil: existingCoupon.useUntil ? dayjs(existingCoupon.useUntil) : undefined,
        address: existingCoupon.address,
        contactPhone: existingCoupon.contactPhone,
        workingHours: existingCoupon.workingHours,
        giftAvailable: existingCoupon.giftAvailable || false,
        images: existingCoupon.images || [],
        options: existingCoupon.options
          ?.filter((o: any) => o.status === 'ACTIVE')
          ?.map((o: any) => ({
            title: o.title,
            regularPrice: o.regularPrice,
            couponPrice: o.couponPrice,
            quantityLimit: o.quantityLimit,
          })) || [],
      });
      if (existingCoupon.coverImageUrl) {
        setCoverImageUrl(existingCoupon.coverImageUrl);
      }
      if (existingCoupon.images?.length > 0) {
        setGalleryImages(existingCoupon.images);
      }
    }
  }, [existingCoupon, form]);

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

  // Быстрое создание мерчанта
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

  // Мутация создания
  const createMutation = useMutation({
    mutationFn: async (values: CouponFormData) => {
      const payload = {
        ...values,
        buyUntil: values.buyUntil.format('YYYY-MM-DDTHH:mm:ss'),
        useUntil: values.useUntil.format('YYYY-MM-DDTHH:mm:ss'),
        images: galleryImages,
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

  // Мутация обновления
  const updateMutation = useMutation({
    mutationFn: async (values: CouponFormData) => {
      const payload = {
        ...values,
        buyUntil: values.buyUntil.format('YYYY-MM-DDTHH:mm:ss'),
        useUntil: values.useUntil.format('YYYY-MM-DDTHH:mm:ss'),
        images: galleryImages,
      };
      const { data } = await api.put(`/api/v1/admin/coupons/${id}`, payload);
      return data;
    },
    onSuccess: () => {
      message.success('Купон успешно обновлён!');
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
      queryClient.invalidateQueries({ queryKey: ['admin-coupon', id] });
      navigate('/moderation/coupons');
    },
    onError: (err: any) => {
      message.error(err.response?.data?.message || 'Ошибка обновления купона');
    }
  });

  const isSaving = createMutation.isPending || updateMutation.isPending;

  const onFinish = (values: CouponFormData) => {
    modal.confirm({
      title: isEditMode ? 'Подтверждение сохранения' : 'Подтверждение публикации',
      icon: <ExclamationCircleOutlined />,
      content: (
        <div>
          <p>Внимательно проверьте все данные. У купона должна быть картинка и правильные цены.</p>
          <Text type="danger">{isEditMode ? 'Сохранить изменения?' : 'Публикуем купон?'}</Text>
        </div>
      ),
      okText: isEditMode ? 'Да, сохранить' : 'Да, опубликовать',
      cancelText: 'Отмена',
      onOk: () => {
        if (isEditMode) {
          updateMutation.mutate(values);
        } else {
          createMutation.mutate(values);
        }
      },
    });
  };

  if (isEditMode && isCouponLoading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: 400 }}>
        <Spin size="large" tip="Загрузка данных купона..." />
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
        <Space>
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/moderation/coupons')} />
          <Title level={4} style={{ margin: 0 }}>
            {isEditMode ? `Редактировать купон #${id}` : 'Создать купон'}
          </Title>
        </Space>
        <Tag color={isEditMode ? 'blue' : 'green'}>
          {isEditMode ? 'Редактирование' : 'Публикуется активно'}
        </Tag>
      </div>

      <Card>
        <Form
          form={form}
          layout="vertical"
          onFinish={onFinish}
          initialValues={{ discountPercent: 0, oldPrice: 0, options: [], giftAvailable: false, images: [] }}
        >
          <Row gutter={24}>
            {/* Левая колонка */}
            <Col xs={24} md={16}>
              <Alert 
                message="Главное" 
                action={<InfoCircleOutlined />} 
                description="Эта информация появится на общей витрине каталога и на Главной странице сайта."
                type="info" showIcon style={{ marginBottom: 16 }} 
              />
              <Form.Item
                name="title"
                label="Название услуги/акции"
                rules={[{ required: true, message: 'Введите название' }]}
                extra="Крупный жирный шрифт в карточке купона."
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

              {/* Галерея дополнительных изображений */}
              <Card type="inner" title="Галерея изображений" style={{ marginBottom: 24 }}
                extra={<Text type="secondary">{galleryImages.length} фото</Text>}
              >
                <Alert
                  message="Дополнительные фото"
                  description="Эти изображения отображаются в слайдере на детальной странице купона. Главное фото (миниатюра) загружается выше."
                  type="info" showIcon style={{ marginBottom: 16 }}
                />
                <Upload
                  name="file"
                  action="http://localhost:8080/api/v1/media/upload"
                  headers={{ Authorization: `Bearer ${useAuthStore.getState().accessToken}` }}
                  listType="picture-card"
                  showUploadList={false}
                  multiple
                  onChange={(info) => {
                    if (info.file.status === 'done') {
                      const urlPath = info.file.response?.data?.url;
                      const fullUrl = `http://localhost:8080${urlPath}`;
                      setGalleryImages(prev => [...prev, fullUrl]);
                      message.success('Фото добавлено в галерею');
                    } else if (info.file.status === 'error') {
                      message.error('Ошибка загрузки фото');
                    }
                  }}
                >
                  <div>
                    <PlusOutlined />
                    <div style={{ marginTop: 8 }}>Добавить фото</div>
                  </div>
                </Upload>
                {galleryImages.length > 0 && (
                  <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 12 }}>
                    {galleryImages.map((url, idx) => (
                      <div key={idx} style={{ position: 'relative', width: 104, height: 104 }}>
                        <img src={url} alt={`gallery-${idx}`} style={{ width: '100%', height: '100%', objectFit: 'cover', borderRadius: 8, border: '1px solid #d9d9d9' }} />
                        <Button
                          type="primary" danger size="small"
                          icon={<DeleteOutlined />}
                          style={{ position: 'absolute', top: 4, right: 4, minWidth: 24, width: 24, height: 24, padding: 0 }}
                          onClick={() => {
                            setGalleryImages(prev => prev.filter((_, i) => i !== idx));
                            message.info('Фото удалено из галереи');
                          }}
                        />
                      </div>
                    ))}
                  </div>
                )}
              </Card>

              <Form.Item name="shortDescription" label="Краткое описание" extra="Пара слов об акции. Отображается прямо на плитке купона в общей ленте.">
                <TextArea rows={2} placeholder="Пара слов об акции..." />
              </Form.Item>

              <Alert 
                message="Блок: О заведении (Полное описание)" 
                description="Поддерживается Markdown! Используйте (-) для списков и (**) для жирного шрифта. Раздел отображается в детальной странице купона снизу."
                type="info" showIcon style={{ marginBottom: 16, marginTop: 16 }} 
              />
              
              <Form.Item name="fullDescription" label="Полное описание">
                <TextArea rows={6} placeholder={"- Разнообразие вкусов\n- VIP-кабинки на 20 человек\n**Рекомендуем попробовать** салат «Мерилин»!"} />
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

              <Alert 
                message="Блок: Важная информация (Условия и Правила)" 
                description="Все три поля поддерживают Markdown! Они группируются сверху карточки с красным значком предупреждения, как на Chocolife."
                type="warning" showIcon style={{ marginBottom: 16 }} 
              />

              <Card type="inner" title="Правила и Условия" style={{ marginBottom: 24, paddingBottom: 0 }}>
                <Form.Item name="terms" label="Условия (ограничения)" extra="Что обязательно нужно знать клиенту.">
                  <TextArea rows={3} placeholder={"- Купон даёт право скидки до 40%\n- Средний счёт — 5 000 тг. (без учёта скидки)\n- Обслуживание 10% оплачивается отдельно"} />
                </Form.Item>
                <Form.Item name="usageRules" label="Общие правила" extra="Когда не работает скидка.">
                  <TextArea rows={3} placeholder={"- Акция не действует на Ифтар сеты\n- Не распространяется на доставку\n- Не суммируется с другими акциями"} />
                </Form.Item>
                <Form.Item name="howToUse" label="Как использовать (Инструкция)" extra="Пошаговое использование.">
                  <TextArea rows={3} placeholder={"1. Сообщите официанту, что у вас купон\n2. Назовите номер или покажите QR-код\n3. После закрытия счета скидка не применяется"} />
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

              <Card type="inner" title="Контакты заведения" style={{ marginBottom: 16 }}>
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

              <Card type="inner" title="Дополнительно">
                <Form.Item name="giftAvailable" label="Доступен как подарок" valuePropName="checked"
                  tooltip="Если включено, у купона появится бейдж '🎁 Подарок' на клиентском сайте"
                >
                  <Switch checkedChildren={<GiftOutlined />} unCheckedChildren="Нет" />
                </Form.Item>
              </Card>

              <Form.Item>
                <Button 
                  type="primary" 
                  htmlType="submit" 
                  size="large" 
                  block 
                  loading={isSaving}
                  style={{ marginTop: 24 }}
                >
                  {isEditMode ? 'Сохранить изменения' : 'Опубликовать купон'}
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
