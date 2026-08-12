import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Card, Form, Input, InputNumber, DatePicker, Select, Switch, Button,
  Upload, Typography, Divider, Space, Alert, App as AntApp, Row, Col, Result, Spin
} from 'antd';
import { PlusOutlined, UploadOutlined, SendOutlined, DeleteOutlined, SaveOutlined } from '@ant-design/icons';
import type { RcFile } from 'antd/es/upload';
import api from '../api';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';

const { Title, Paragraph } = Typography;
const { TextArea } = Input;

interface CategoryItem {
  id: number;
  name: string;
  slug: string;
}

interface OptionField {
  key: number;
  title: string;
  regularPrice: number | null;
  couponPrice: number | null;
  quantityLimit: number | null;
}

interface CouponFormValues {
  title: string;
  categoryId: number;
  offerDescription: string;
  oldPrice: number;
  fromPrice: number;
  discountPercent?: number;
  buyUntil: Dayjs;
  useUntil: Dayjs;
  giftAvailable?: boolean;
}

interface CouponDetail {
  id: number;
  title: string;
  category?: { id: number } | null;
  offerDescription?: string;
  oldPrice?: number;
  fromPrice?: number;
  discountPercent?: number;
  buyUntil?: string;
  useUntil?: string;
  giftAvailable?: boolean;
  coverImageUrl?: string;
  status: string;
  revisionComment?: string;
  images?: string[];
  options?: Array<{
    id?: number;
    title: string;
    regularPrice: number;
    couponPrice: number;
    quantityLimit?: number | null;
  }>;
}

const EDITABLE_STATUSES = new Set(['LEAD', 'DRAFT', 'REVISION_REQUESTED']);

const fetchCategories = async (): Promise<CategoryItem[]> => {
  const res = await api.get('/api/v1/categories');
  return res.data.data || res.data;
};

// Persisted option IDs are positive. Keep temporary client keys in a distant
// negative range so newly added rows cannot collide with server-provided IDs.
let optionKeyCounter = -1_000_000;

export default function CouponRequestFormPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const isEditMode = id !== undefined;
  const couponId = Number(id);
  const hasValidCouponId = Number.isInteger(couponId) && couponId > 0;

  const {
    data: coupon,
    isLoading: couponLoading,
    error: couponError,
  } = useQuery({
    queryKey: ['partner-coupon', couponId],
    enabled: isEditMode && hasValidCouponId,
    queryFn: async (): Promise<CouponDetail> => {
      const res = await api.get(`/api/v1/partner/coupons/${couponId}`);
      return res.data.data;
    },
  });

  if (isEditMode && !hasValidCouponId) {
    return <Result status="404" title="Предложение не найдено" />;
  }
  if (isEditMode && couponLoading) {
    return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  }
  if (isEditMode && (couponError || !coupon)) {
    return <Result status="error" title="Не удалось загрузить предложение" />;
  }
  if (coupon && !EDITABLE_STATUSES.has(coupon.status)) {
    return (
      <Result
        status="403"
        title="Редактирование недоступно"
        subTitle={`Предложение в статусе ${coupon.status} нельзя изменять`}
        extra={<Button onClick={() => navigate('/coupons')}>Вернуться к предложениям</Button>}
      />
    );
  }

  return (
    <CouponRequestForm
      coupon={coupon}
      couponId={couponId}
      isEditMode={isEditMode}
    />
  );
}

interface CouponRequestFormProps {
  coupon?: CouponDetail;
  couponId: number;
  isEditMode: boolean;
}

function CouponRequestForm({ coupon, couponId, isEditMode }: CouponRequestFormProps) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message: antMessage } = AntApp.useApp();
  const [form] = Form.useForm();

  const { data: categories = [], isLoading: catLoading } = useQuery({
    queryKey: ['categories'],
    queryFn: fetchCategories,
  });

  const [options, setOptions] = useState<OptionField[]>(() => coupon?.options?.length
    ? coupon.options.map((option, index) => ({
        key: option.id ?? -(index + 1),
        title: option.title,
        regularPrice: option.regularPrice,
        couponPrice: option.couponPrice,
        quantityLimit: option.quantityLimit ?? null,
      }))
    : [{ key: 0, title: '', regularPrice: null, couponPrice: null, quantityLimit: null }]);
  const [uploadedUrls, setUploadedUrls] = useState<string[]>(() => coupon?.images?.length
    ? coupon.images
    : coupon?.coverImageUrl ? [coupon.coverImageUrl] : []);
  const [uploading, setUploading] = useState(false);

  const initialValues = coupon ? {
    title: coupon.title,
    categoryId: coupon.category?.id,
    offerDescription: coupon.offerDescription,
    oldPrice: coupon.oldPrice,
    fromPrice: coupon.fromPrice,
    discountPercent: coupon.discountPercent,
    buyUntil: coupon.buyUntil ? dayjs(coupon.buyUntil) : undefined,
    useUntil: coupon.useUntil ? dayjs(coupon.useUntil) : undefined,
    giftAvailable: coupon.giftAvailable ?? false,
  } : undefined;

  const saveMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) => isEditMode
      ? api.put(`/api/v1/partner/coupons/${couponId}`, data)
      : api.post('/api/v1/partner/coupons', data),
    onSuccess: () => {
      antMessage.success(isEditMode
        ? 'Изменения сохранены'
        : 'Купонное предложение отправлено! sizbiz свяжется с вами для оформления.');
      queryClient.invalidateQueries({ queryKey: ['partner-coupons'] });
      if (isEditMode) {
        queryClient.invalidateQueries({ queryKey: ['partner-coupon', couponId] });
      }
      navigate('/coupons');
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      const msg = e.response?.data?.message
        || (isEditMode ? 'Ошибка при сохранении изменений' : 'Ошибка при создании заявки');
      antMessage.error(msg);
    },
  });

  const addOption = () => {
    setOptions([...options, {
      key: optionKeyCounter--,
      title: '', regularPrice: null, couponPrice: null, quantityLimit: null
    }]);
  };

  const removeOption = (key: number) => {
    if (options.length <= 1) return;
    setOptions(options.filter(o => o.key !== key));
  };

  const updateOption = (key: number, field: string, value: unknown) => {
    setOptions(options.map(o => o.key === key ? { ...o, [field]: value } : o));
  };

  const handleUpload = async (file: RcFile) => {
    if (uploadedUrls.length >= 5) {
      antMessage.warning('Можно загрузить до 5 фотографий');
      return false;
    }
    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      // Do not set Content-Type manually: the browser must add the multipart boundary.
      const res = await api.post('/api/v1/media/upload', formData);
      const url = res.data?.data?.url || res.data?.url || res.data?.data;
      if (url) {
        setUploadedUrls(prev => [...prev, url]);
        antMessage.success('Фото загружено');
      }
    } catch {
      antMessage.error('Ошибка загрузки фото. Вы можете отправить заявку без фото.');
    } finally {
      setUploading(false);
    }
    return false; // prevent antd auto-upload
  };

  const removePhoto = (idx: number) => {
    setUploadedUrls(prev => prev.filter((_, i) => i !== idx));
  };

  /** Автоматический расчёт % скидки из старой и новой цены. */
  const autoCalcDiscount = () => {
    // setTimeout чтобы дать antd обновить значения формы
    setTimeout(() => {
      const oldPrice = form.getFieldValue('oldPrice');
      const fromPrice = form.getFieldValue('fromPrice');
      if (oldPrice && fromPrice && oldPrice > 0 && fromPrice < oldPrice) {
        const pct = Math.round(((oldPrice - fromPrice) / oldPrice) * 100);
        form.setFieldValue('discountPercent', Math.max(1, Math.min(pct, 99)));
      }
    }, 0);
  };

  const onFinish = (values: CouponFormValues) => {
    const payload = {
      title: values.title,
      categoryId: values.categoryId,
      offerDescription: values.offerDescription,
      oldPrice: values.oldPrice,
      fromPrice: values.fromPrice,
      discountPercent: values.discountPercent && values.discountPercent > 0 ? values.discountPercent : null,
      buyUntil: values.buyUntil?.toISOString(),
      useUntil: values.useUntil?.toISOString(),
      giftAvailable: values.giftAvailable || false,
      imageUrls: uploadedUrls.length > 0 ? uploadedUrls : null,
      coverImageUrl: uploadedUrls.length > 0 ? uploadedUrls[0] : null,
      options: options
        .filter(o => o.title && o.regularPrice && o.couponPrice)
        .map(o => ({
          title: o.title,
          regularPrice: o.regularPrice,
          couponPrice: o.couponPrice,
          quantityLimit: o.quantityLimit,
        })),
    };

    if (payload.options.length === 0) {
      antMessage.error('Добавьте хотя бы один вариант предложения');
      return;
    }

    saveMutation.mutate(payload);
  };

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <Title level={3}>
        {isEditMode ? '✏️ Изменить предложение' : '🎯 Подать купонное предложение'}
      </Title>
      <Paragraph type="secondary">
        {isEditMode
          ? 'Проверьте данные и сохраните изменения. sizbiz повторно рассмотрит обновлённое предложение.'
          : 'Заполните основную информацию о предложении. sizbiz поможет с оформлением и публикацией.'}
      </Paragraph>

      {isEditMode && coupon?.status === 'REVISION_REQUESTED' && coupon.revisionComment && (
        <Alert
          type="warning"
          showIcon
          title="Что нужно уточнить"
          description={coupon.revisionComment}
          style={{ marginBottom: 16 }}
        />
      )}

      <Card style={{ borderRadius: 12 }}>
        <Form
          form={form}
          layout="vertical"
          initialValues={initialValues}
          onFinish={onFinish}
          requiredMark="optional"
        >

          <Form.Item name="title" label="Название предложения" rules={[{ required: true, message: 'Укажите название' }]}>
            <Input placeholder="Например: Скидка 50% на маникюр" maxLength={200} showCount />
          </Form.Item>

          <Form.Item name="categoryId" label="Категория" rules={[{ required: true, message: 'Выберите категорию' }]}>
            <Select
              placeholder="Выберите категорию"
              loading={catLoading}
              showSearch
              optionFilterProp="label"
              style={{ width: '100%' }}
              options={categories.map(c => ({ value: c.id, label: c.name }))}
            />
          </Form.Item>

          <Form.Item name="offerDescription" label="Описание предложения" rules={[{ required: true, message: 'Опишите предложение' }]}>
            <TextArea
              rows={4}
              placeholder="Опишите, что входит в предложение, условия использования и ограничения"
              maxLength={5000}
              showCount
            />
          </Form.Item>

          <Divider>Цены и скидка</Divider>

          <Row gutter={16}>
            <Col xs={24} sm={8}>
              <Form.Item name="oldPrice" label="Обычная цена" rules={[{ required: true, message: 'Укажите цену' }]}>
                <InputNumber
                  min={1}
                  style={{ width: '100%' }}
                  placeholder="100 000"
                  addonAfter="сум"
                  onChange={() => autoCalcDiscount()}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="fromPrice" label="Цена по предложению" rules={[{ required: true, message: 'Укажите цену' }]}>
                <InputNumber
                  min={1}
                  style={{ width: '100%' }}
                  placeholder="50 000"
                  addonAfter="сум"
                  onChange={() => autoCalcDiscount()}
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={8}>
              <Form.Item name="discountPercent" label="Скидка %" help="Рассчитывается автоматически">
                <InputNumber min={1} max={99} style={{ width: '100%' }} placeholder="авто" addonAfter="%" />
              </Form.Item>
            </Col>
          </Row>

          <Divider>Сроки</Divider>

          <Row gutter={16}>
            <Col xs={24} sm={12}>
              <Form.Item name="buyUntil" label="Купить до" rules={[{ required: true, message: 'Укажите срок' }]}>
                <DatePicker
                  showTime
                  style={{ width: '100%' }}
                  disabledDate={(d) => d.isBefore(dayjs(), 'day')}
                  placeholder="Выберите дату"
                />
              </Form.Item>
            </Col>
            <Col xs={24} sm={12}>
              <Form.Item name="useUntil" label="Использовать до" rules={[{ required: true, message: 'Укажите срок' }]}>
                <DatePicker
                  showTime
                  style={{ width: '100%' }}
                  disabledDate={(d) => d.isBefore(dayjs(), 'day')}
                  placeholder="Выберите дату"
                />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item name="giftAvailable" label="Можно подарить" valuePropName="checked">
            <Switch />
          </Form.Item>

          <Divider>Варианты предложения</Divider>

          {options.map((opt, idx) => (
            <Card
              key={opt.key}
              size="small"
              title={`Вариант ${idx + 1}`}
              style={{ marginBottom: 12, background: '#fafafa', borderRadius: 8 }}
              extra={options.length > 1 ? (
                <Button type="text" danger icon={<DeleteOutlined />} onClick={() => removeOption(opt.key)} />
              ) : null}
            >
              <Row gutter={16} align="top">
                <Col xs={24} sm={12} md={6}>
                  <div style={{ marginBottom: 4, fontSize: 13, color: '#555' }}>Название</div>
                  <Input
                    placeholder="Базовый / VIP"
                    value={opt.title}
                    onChange={e => updateOption(opt.key, 'title', e.target.value)}
                  />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <div style={{ marginBottom: 4, fontSize: 13, color: '#555' }}>Обычная цена (сум)</div>
                  <InputNumber
                    min={1} style={{ width: '100%' }}
                    placeholder="100 000"
                    value={opt.regularPrice}
                    onChange={v => updateOption(opt.key, 'regularPrice', v)}
                  />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <div style={{ marginBottom: 4, fontSize: 13, color: '#555' }}>Цена по предложению (сум)</div>
                  <InputNumber
                    min={1} style={{ width: '100%' }}
                    placeholder="50 000"
                    value={opt.couponPrice}
                    onChange={v => updateOption(opt.key, 'couponPrice', v)}
                  />
                </Col>
                <Col xs={24} sm={12} md={6}>
                  <div style={{ marginBottom: 4, fontSize: 13, color: '#555' }}>Лимит (шт.)</div>
                  <InputNumber
                    min={1} style={{ width: '100%' }}
                    placeholder="100"
                    value={opt.quantityLimit}
                    onChange={v => updateOption(opt.key, 'quantityLimit', v)}
                  />
                </Col>
              </Row>
            </Card>
          ))}


          <Button type="dashed" block icon={<PlusOutlined />} onClick={addOption} style={{ marginBottom: 24 }}>
            Добавить вариант
          </Button>

          <Divider>Фотографии (необязательно)</Divider>

          <Alert
            type="info"
            showIcon
            message="Фото необязательно"
            description="Если есть фото услуги, блюда, помещения или результата работы — добавьте. Если нет, sizbiz поможет оформить предложение."
            style={{ marginBottom: 16 }}
          />

          <Space wrap style={{ marginBottom: 16 }}>
            {uploadedUrls.map((url, idx) => (
              <div key={idx} style={{ position: 'relative', display: 'inline-block' }}>
                <img src={url} alt={`Фото ${idx + 1}`} style={{ width: 80, height: 80, objectFit: 'cover', borderRadius: 8 }} />
                <Button
                  type="text" danger size="small"
                  icon={<DeleteOutlined />}
                  onClick={() => removePhoto(idx)}
                  style={{ position: 'absolute', top: -8, right: -8, background: '#fff', borderRadius: '50%' }}
                />
              </div>
            ))}
          </Space>

          {uploadedUrls.length < 5 && (
            <Upload
              showUploadList={false}
              beforeUpload={handleUpload}
              accept="image/*"
            >
              <Button icon={<UploadOutlined />} loading={uploading}>
                Загрузить фото ({uploadedUrls.length}/5)
              </Button>
            </Upload>
          )}

          <Divider />

          <Form.Item>
            <Space>
              <Button
                type="primary"
                htmlType="submit"
                icon={isEditMode ? <SaveOutlined /> : <SendOutlined />}
                loading={saveMutation.isPending}
                size="large"
              >
                {isEditMode ? 'Сохранить изменения' : 'Отправить заявку'}
              </Button>
              <Button onClick={() => navigate('/coupons')}>Отмена</Button>
            </Space>
          </Form.Item>

        </Form>
      </Card>
    </div>
  );
}
