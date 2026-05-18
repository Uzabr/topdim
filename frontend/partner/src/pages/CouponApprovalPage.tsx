import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Alert, App as AntApp, Button, Card, Col, Descriptions, Divider,
  Image, Input, Modal, Result, Row, Space, Spin, Tag, Typography
} from 'antd';
import {
  ArrowLeftOutlined, CheckCircleOutlined, CloseCircleOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import api from '../api';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

interface CouponOption {
  id: number;
  title: string;
  regularPrice: number;
  couponPrice: number;
  quantityLimit: number | null;
  quantitySold: number;
  status: string;
}

interface MerchantLocation {
  id?: number;
  title?: string;
  address?: string;
  phone?: string;
  workingHours?: string;
  active?: boolean;
}

interface MerchantSummary {
  id: number;
  name: string;
  logoUrl?: string;
  description?: string;
  primaryLocation?: MerchantLocation | null;
}

interface CategorySummary {
  id: number;
  name: string;
  slug: string;
  iconUrl?: string;
}

interface CouponDetail {
  id: number;
  title: string;
  offerDescription?: string;
  merchant?: MerchantSummary | null;
  category?: CategorySummary | null;
  oldPrice?: number;
  fromPrice: number;
  discountPercent?: number;
  coverImageUrl?: string;
  status: string;
  revisionComment?: string;
  buyUntil?: string;
  useUntil?: string;
  images?: string[];
  options?: CouponOption[];
}

function formatPrice(value?: number | null) {
  if (typeof value !== 'number') return '—';
  return `${value.toLocaleString('ru-RU')} сум`;
}

function formatDate(value?: string | null) {
  if (!value) return '—';
  return dayjs(value).format('DD.MM.YYYY HH:mm');
}

export default function CouponApprovalPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message, modal } = AntApp.useApp();
  const [revisionOpen, setRevisionOpen] = useState(false);
  const [revisionComment, setRevisionComment] = useState('');

  const couponId = Number(id);

  const { data: coupon, isLoading, error } = useQuery({
    queryKey: ['partner-coupon', couponId],
    enabled: Number.isFinite(couponId),
    queryFn: async (): Promise<CouponDetail> => {
      const res = await api.get(`/api/v1/partner/coupons/${couponId}`);
      return res.data.data;
    },
  });

  const approveMutation = useMutation({
    mutationFn: () => api.post(`/api/v1/partner/coupons/${couponId}/approve`),
    onSuccess: async () => {
      message.success('Купон одобрен и опубликован');
      await queryClient.invalidateQueries({ queryKey: ['partner-coupons'] });
      navigate('/coupons');
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      message.error(e.response?.data?.message || 'Не удалось одобрить купон');
    },
  });

  const revisionMutation = useMutation({
    mutationFn: (comment: string) =>
      api.post(`/api/v1/partner/coupons/${couponId}/request-revision`, { comment }),
    onSuccess: async () => {
      message.success('Купон возвращён TopDim на доработку');
      await queryClient.invalidateQueries({ queryKey: ['partner-coupons'] });
      navigate('/coupons');
    },
    onError: (err: unknown) => {
      const e = err as { response?: { data?: { message?: string } } };
      message.error(e.response?.data?.message || 'Не удалось отправить правки');
    },
  });

  const handleApprove = () => {
    if (!coupon) return;
    modal.confirm({
      title: 'Одобрить и опубликовать купон?',
      icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      content: `Купон "${coupon.title}" станет доступен клиентам.`,
      okText: 'Одобрить и опубликовать',
      cancelText: 'Отмена',
      onOk: () => approveMutation.mutate(),
    });
  };

  const handleRevisionSubmit = () => {
    const trimmed = revisionComment.trim();
    if (!trimmed) {
      message.warning('Опишите, что нужно исправить');
      return;
    }
    revisionMutation.mutate(trimmed);
  };

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  if (error || !coupon) return <Result status="error" title="Купон не найден" />;

  const isWaiting = coupon.status === 'WAITING_FOR_MERCHANT';

  return (
    <div style={{ maxWidth: 1040, margin: '0 auto' }}>
      <Space style={{ marginBottom: 16 }}>
        <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/coupons')}>
          Назад к купонам
        </Button>
        <Tag color={isWaiting ? 'orange' : 'default'}>{coupon.status}</Tag>
      </Space>

      <Row gutter={[24, 24]}>
        <Col xs={24} lg={10}>
          <Card style={{ borderRadius: 16 }}>
            {coupon.coverImageUrl ? (
              <Image
                src={coupon.coverImageUrl}
                alt={coupon.title}
                style={{ width: '100%', maxHeight: 360, objectFit: 'cover', borderRadius: 12 }}
              />
            ) : (
              <Alert type="warning" showIcon message="У купона нет обложки" />
            )}
            {coupon.images && coupon.images.length > 0 && (
              <>
                <Divider />
                <Image.PreviewGroup>
                  <Space wrap>
                    {coupon.images.map((url, index) => (
                      <Image key={url + index} src={url} width={86} height={64} style={{ objectFit: 'cover', borderRadius: 8 }} />
                    ))}
                  </Space>
                </Image.PreviewGroup>
              </>
            )}
          </Card>
        </Col>

        <Col xs={24} lg={14}>
          <Card style={{ borderRadius: 16 }}>
            <Title level={3}>{coupon.title}</Title>
            <Space align="baseline" wrap>
              <Text strong style={{ fontSize: 24, color: '#1677ff' }}>
                {formatPrice(coupon.fromPrice)}
              </Text>
              {coupon.oldPrice ? <Text delete type="secondary">{formatPrice(coupon.oldPrice)}</Text> : null}
              {coupon.discountPercent ? <Tag color="red">-{coupon.discountPercent}%</Tag> : null}
            </Space>

            <Divider />
            <Paragraph style={{ whiteSpace: 'pre-wrap' }}>
              {coupon.offerDescription || 'Описание не указано'}
            </Paragraph>

            <Divider>Бизнес и контакты</Divider>
            {!coupon.merchant?.primaryLocation?.address && (
              <Alert
                type="warning"
                showIcon
                message="Адрес primary location не указан"
                description="Без корректного адреса купон может не пройти публикацию. Проверьте данные перед одобрением."
                style={{ marginBottom: 16 }}
              />
            )}
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="Партнёр">
                {coupon.merchant?.name || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Категория">
                {coupon.category?.name || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Адрес">
                {coupon.merchant?.primaryLocation?.address || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Телефон">
                {coupon.merchant?.primaryLocation?.phone || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Время работы">
                {coupon.merchant?.primaryLocation?.workingHours || '—'}
              </Descriptions.Item>
            </Descriptions>

            <Divider>Сроки</Divider>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="Купить до">{formatDate(coupon.buyUntil)}</Descriptions.Item>
              <Descriptions.Item label="Использовать до">{formatDate(coupon.useUntil)}</Descriptions.Item>
            </Descriptions>

            {coupon.options && coupon.options.length > 0 && (
              <>
                <Divider>Варианты</Divider>
                <Space direction="vertical" style={{ width: '100%' }}>
                  {coupon.options.map((option) => (
                    <Card key={option.id} size="small">
                      <Space direction="vertical" size={2}>
                        <Text strong>{option.title}</Text>
                        <Text>
                          {formatPrice(option.couponPrice)}
                          {' '}
                          <Text delete type="secondary">{formatPrice(option.regularPrice)}</Text>
                        </Text>
                        <Text type="secondary">
                          Лимит: {option.quantityLimit || 'без лимита'}, продано: {option.quantitySold}
                        </Text>
                      </Space>
                    </Card>
                  ))}
                </Space>
              </>
            )}

            <Divider />
            {!isWaiting ? (
              <Alert
                type="info"
                showIcon
                message="Этот купон сейчас не ожидает вашего согласования"
                description="Действия доступны только для статуса WAITING_FOR_MERCHANT."
              />
            ) : (
              <Space wrap>
                <Button
                  type="primary"
                  size="large"
                  icon={<CheckCircleOutlined />}
                  loading={approveMutation.isPending}
                  onClick={handleApprove}
                >
                  Одобрить и опубликовать
                </Button>
                <Button
                  danger
                  size="large"
                  icon={<CloseCircleOutlined />}
                  onClick={() => setRevisionOpen(true)}
                >
                  Запросить правки
                </Button>
              </Space>
            )}
          </Card>
        </Col>
      </Row>

      <Modal
        title="Что нужно исправить?"
        open={revisionOpen}
        onCancel={() => setRevisionOpen(false)}
        onOk={handleRevisionSubmit}
        okText="Отправить правки"
        cancelText="Отмена"
        confirmLoading={revisionMutation.isPending}
        okButtonProps={{ danger: true }}
      >
        <Alert
          type="info"
          showIcon
          message="Комментарий увидит команда TopDim"
          description="Напишите конкретно: цена, текст, сроки, фото или условия акции."
          style={{ marginBottom: 16 }}
        />
        <TextArea
          rows={5}
          value={revisionComment}
          onChange={(event) => setRevisionComment(event.target.value)}
          maxLength={2000}
          showCount
          placeholder="Например: нужно изменить цену на 89 000 сум и добавить условие только по будням."
        />
      </Modal>
    </div>
  );
}
