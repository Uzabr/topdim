import { Card, Form, Input, InputNumber, Button, Typography, App, Row, Col, DatePicker, Select, Tag } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';
import api from '../../api/client';

const { Title, Text } = Typography;
const { TextArea } = Input;

interface CreateCouponFormData {
  title: string;
  categoryId: number;
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
}

export const CreateCouponPage = () => {
  const [form] = Form.useForm<CreateCouponFormData>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();

  // Загружаем категории для селекта
  const { data: categories } = useQuery({
    queryKey: ['categories'],
    queryFn: async () => {
      // Пока замокаем, так как эндпоинт списка категорий может быть в merchant-service или admin-котроллере
      // const res = await api.get('/api/v1/admin/categories'); 
      return [
        { id: 1, name: 'Еда и напитки' },
        { id: 2, name: 'Красота и SPA' },
        { id: 3, name: 'Развлечения' }
      ];
    }
  });

  const createMutation = useMutation({
    mutationFn: async (values: any) => {
      // Подготовка данных, парсинг дат
      const payload = {
        ...values,
        merchantId: null, // Независимый купон
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
          <p>Внимательно проверьте все данные (цены, орфографию). Купон сразу станет активным на платформе без этапа проверки.</p>
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
          initialValues={{ discountPercent: 0, oldPrice: 0 }}
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
                    name="categoryId"
                    label="Категория"
                    rules={[{ required: true, message: 'Выберите категорию' }]}
                  >
                    <Select placeholder="Выберите категорию">
                      {categories?.map((c) => (
                        <Select.Option key={c.id} value={c.id}>{c.name}</Select.Option>
                      ))}
                    </Select>
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="discountPercent"
                    label="Процент скидки (%)"
                  >
                    <InputNumber min={0} max={100} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Row gutter={16}>
                <Col span={12}>
                  <Form.Item name="oldPrice" label="Старая цена (сум)">
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
                <Col span={12}>
                  <Form.Item
                    name="fromPrice"
                    label="Новая цена от (сум)"
                    rules={[{ required: true, message: 'Обязательное поле' }]}
                  >
                    <InputNumber min={0} style={{ width: '100%' }} />
                  </Form.Item>
                </Col>
              </Row>

              <Form.Item name="shortDescription" label="Краткое описание (для карточки товара)">
                <TextArea rows={2} placeholder="Пара слов об акции..." />
              </Form.Item>

              <Form.Item name="fullDescription" label="Полное описание">
                <TextArea rows={6} placeholder="Подробное описание услуг и преимуществ..." />
              </Form.Item>
            </Col>

            {/* Правая колонка (Сайдбар формы) */}
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
                <Form.Item name="address" label="Адрес проведения (без привязки к магазину)">
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
    </div>
  );
};
