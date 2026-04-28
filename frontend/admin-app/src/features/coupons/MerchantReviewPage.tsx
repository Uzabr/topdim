import { useState } from 'react';
import { Alert, App, Button, Card, Col, Descriptions, Divider, Empty, Image, Input, Modal, Row, Space, Spin, Tag, Typography, theme } from 'antd';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  EyeOutlined,
  MessageOutlined,
  ShopOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { AxiosError } from 'axios';
import api from '../../api/client';
import { getPublicationReadiness } from './publicationReadiness';

const { Paragraph, Text, Title } = Typography;
const { TextArea } = Input;

interface MerchantSummary {
  id: number;
  name: string;
  logoUrl?: string;
  description?: string;
  primaryLocation?: {
    id: number;
    address?: string;
    phone?: string;
    workingHours?: string;
    active?: boolean;
  };
}

interface CategorySummary {
  id: number;
  name: string;
}

interface CouponOption {
  id: number;
  title: string;
  regularPrice: number;
  couponPrice: number;
  quantityLimit: number;
  quantitySold: number;
}

interface Coupon {
  id: number;
  title: string;
  /** Canonical offer description (Release 1+). */
  offerDescription?: string;
  merchant: MerchantSummary | null;
  category?: CategorySummary | null;
  fromPrice: number;
  oldPrice: number | null;
  discountPercent: number | null;
  coverImageUrl?: string;
  status: string;
  revisionComment: string | null;
  options?: CouponOption[];
  images?: string[];
  buyUntil?: string;
  useUntil?: string;
}

interface CouponsPagePayload {
  content: Coupon[];
}

interface ApiResponse<T> {
  data: T;
  message?: string | null;
}

interface ApiErrorResponse {
  message?: string;
}

function getErrorMessage(error: unknown, fallback: string): string {
  const axiosError = error as AxiosError<ApiErrorResponse>;
  return axiosError.response?.data?.message ?? fallback;
}

function formatPrice(value: number | null | undefined): string {
  if (typeof value !== 'number') return '-';
  return `${value.toLocaleString('ru-RU')} сум`;
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '-';
  return new Date(value).toLocaleDateString('ru-RU', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export const MerchantReviewPage = () => {
  const queryClient = useQueryClient();
  const { message, modal } = App.useApp();
  const { token } = theme.useToken();

  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectingCouponId, setRejectingCouponId] = useState<number | null>(null);
  const [rejectReason, setRejectReason] = useState('');
  const [previewCoupon, setPreviewCoupon] = useState<Coupon | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  const { data: coupons = [], isLoading, isError } = useQuery({
    queryKey: ['merchant-review-coupons'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<Coupon[] | CouponsPagePayload>>(
        '/api/v1/admin/coupons',
        { params: { status: 'WAITING_FOR_MERCHANT', size: 100 } }
      );
      const data = res.data.data;
      return Array.isArray(data) ? data : data.content;
    },
  });

  const approveMutation = useMutation({
    mutationFn: async (couponId: number) => {
      await api.patch(`/api/v1/mod/coupons/${couponId}/review`, { status: 'APPROVE' });
      return couponId;
    },
    onSuccess: () => {
      message.success('Купон одобрен и опубликован!');
      setPreviewCoupon(null);
      queryClient.invalidateQueries({ queryKey: ['merchant-review-coupons'] });
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
    },
    onError: (error: unknown) => {
      message.error(getErrorMessage(error, 'Не удалось одобрить купон'));
    },
  });

  const rejectMutation = useMutation({
    mutationFn: async ({ couponId, reason }: { couponId: number; reason: string }) => {
      await api.patch(`/api/v1/mod/coupons/${couponId}/review`, { status: 'REJECT', reason });
      return couponId;
    },
    onSuccess: () => {
      message.success('Купон возвращён на доработку');
      setRejectModalOpen(false);
      setRejectReason('');
      setRejectingCouponId(null);
      setPreviewCoupon(null);
      queryClient.invalidateQueries({ queryKey: ['merchant-review-coupons'] });
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
    },
    onError: (error: unknown) => {
      message.error(getErrorMessage(error, 'Не удалось отклонить купон'));
    },
  });

  const handleOpenPreview = async (couponId: number) => {
    setPreviewLoading(true);
    try {
      const res = await api.get<ApiResponse<Coupon>>(`/api/v1/admin/coupons/${couponId}`);
      setPreviewCoupon(res.data.data);
    } catch {
      message.error('Не удалось загрузить данные купона');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleApprove = (couponId: number, title: string) => {
    modal.confirm({
      title: 'Одобрить купон?',
      icon: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      content: (
        <div>
          <p>Купон <strong>"{title}"</strong> будет опубликован и станет доступен клиентам.</p>
          <Text type="warning">Это действие имитирует одобрение от мерчанта.</Text>
        </div>
      ),
      okText: 'Одобрить',
      okType: 'primary',
      cancelText: 'Отмена',
      onOk: () => approveMutation.mutate(couponId),
    });
  };

  const handleRejectClick = (couponId: number) => {
    setRejectingCouponId(couponId);
    setRejectReason('');
    setRejectModalOpen(true);
  };

  const handleRejectConfirm = () => {
    if (!rejectingCouponId || !rejectReason.trim()) {
      message.warning('Укажите причину отклонения');
      return;
    }
    rejectMutation.mutate({ couponId: rejectingCouponId, reason: rejectReason.trim() });
  };

  return (
    <div>
      <div style={{ marginBottom: 24 }}>
        <Title level={4} style={{ margin: 0 }}>Ожидают подтверждения мерчанта</Title>
        <Text type="secondary">
          Support-only экран: используйте только если партнёр не может подтвердить купон в своём кабинете.
        </Text>
      </div>

      <Alert
        type="info"
        showIcon
        icon={<ClockCircleOutlined />}
        message="Как работает этот экран"
        description="Основной MVP-флоу: партнёр подтверждает купон в partner cabinet. Этот экран нужен только как ручной support fallback."
        style={{ marginBottom: 24 }}
      />

      {isError && (
        <Alert type="warning" showIcon closable message="Бэкенд не отвечает" style={{ marginBottom: 16 }} />
      )}

      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: 60 }}><Spin size="large" /></div>
      ) : coupons.length === 0 ? (
        <Card><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Нет купонов, ожидающих подтверждения" /></Card>
      ) : (
        <Row gutter={[16, 16]}>
          {coupons.map((coupon) => (
            <Col key={coupon.id} xs={24} sm={12} lg={8} xl={6}>
              <Card
                size="small"
                title={
                  <Space size={8} wrap>
                    <Text strong>#{coupon.id}</Text>
                    <Tag color="purple">WAITING</Tag>
                  </Space>
                }
                style={{ borderLeft: `4px solid ${token.colorPrimary}` }}
              >
                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  <div>
                    <Paragraph strong style={{ marginBottom: 4 }}>{coupon.title}</Paragraph>
                    <Space size={6} wrap>
                      <ShopOutlined />
                      <Text type="secondary">{coupon.merchant?.name ?? 'Не указан'}</Text>
                    </Space>
                  </div>

                  <div>
                    <Text strong>{formatPrice(coupon.fromPrice)}</Text>
                    {coupon.oldPrice && (
                      <Text type="secondary" delete style={{ marginLeft: 8 }}>{formatPrice(coupon.oldPrice)}</Text>
                    )}
                    {typeof coupon.discountPercent === 'number' && coupon.discountPercent > 0 && (
                      <Tag color="volcano" style={{ marginLeft: 8 }}>-{coupon.discountPercent}%</Tag>
                    )}
                  </div>

                  <Space direction="vertical" size={8} style={{ width: '100%' }}>
                    <Button
                      icon={<EyeOutlined />}
                      block
                      loading={previewLoading}
                      onClick={() => handleOpenPreview(coupon.id)}
                    >
                      Подробнее
                    </Button>
                    <Button
                      type="primary"
                      icon={<CheckCircleOutlined />}
                      block
                      loading={approveMutation.isPending && approveMutation.variables === coupon.id}
                      onClick={() => handleApprove(coupon.id, coupon.title)}
                    >
                      Одобрить → ACTIVE
                    </Button>
                    <Button
                      danger
                      icon={<CloseCircleOutlined />}
                      block
                      onClick={() => handleRejectClick(coupon.id)}
                    >
                      Отклонить → REVISION
                    </Button>
                  </Space>
                </Space>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      {/* ===== Полный превью купона ===== */}
      <Modal
        title={previewCoupon ? `Купон #${previewCoupon.id}: ${previewCoupon.title}` : 'Загрузка...'}
        open={!!previewCoupon}
        onCancel={() => setPreviewCoupon(null)}
        width={800}
        footer={previewCoupon ? (() => {
          const previewReadiness = getPublicationReadiness(previewCoupon.merchant);
          return (
            <Space direction="vertical" size={12} style={{ width: '100%' }}>
              {!previewReadiness.ready && (
                <Alert
                  type="error"
                  showIcon
                  title="Купон нельзя публиковать"
                  description={
                    <ul style={{ margin: 0, paddingLeft: 18 }}>
                      {previewReadiness.reasons.map((reason) => <li key={reason}>{reason}</li>)}
                    </ul>
                  }
                />
              )}
              <Space>
                <Button onClick={() => setPreviewCoupon(null)}>Закрыть</Button>
                <Button danger onClick={() => { handleRejectClick(previewCoupon.id); }}>Отклонить</Button>
                <Button type="primary" disabled={!previewReadiness.ready}
                  onClick={() => handleApprove(previewCoupon.id, previewCoupon.title)}
                  loading={approveMutation.isPending}>
                  Одобрить → ACTIVE
                </Button>
              </Space>
            </Space>
          );
        })() : null}
      >
        {previewCoupon && (
          <div>
            {/* Обложка */}
            {previewCoupon.coverImageUrl && (
              <div style={{ textAlign: 'center', marginBottom: 16 }}>
                <Image
                  src={previewCoupon.coverImageUrl}
                  alt="Обложка"
                  style={{ maxHeight: 300, objectFit: 'cover', borderRadius: 8 }}
                  fallback="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgeG1sbnM9Imh0dHA6Ly93d3cudzMub3JnLzIwMDAvc3ZnIj48cmVjdCB3aWR0aD0iMjAwIiBoZWlnaHQ9IjIwMCIgZmlsbD0iI2YwZjBmMCIvPjx0ZXh0IHg9IjUwJSIgeT0iNTAlIiBkb21pbmFudC1iYXNlbGluZT0ibWlkZGxlIiB0ZXh0LWFuY2hvcj0ibWlkZGxlIiBmaWxsPSIjYmJiIj5ObyBJbWFnZTwvdGV4dD48L3N2Zz4="
                />
              </div>
            )}

            {/* Основная информация */}
            <Descriptions bordered column={2} size="small">
              <Descriptions.Item label="Мерчант" span={1}>
                {previewCoupon.merchant?.name ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Категория" span={1}>
                {previewCoupon.category?.name ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Старая цена" span={1}>
                {formatPrice(previewCoupon.oldPrice)}
              </Descriptions.Item>
              <Descriptions.Item label="Цена по купону" span={1}>
                <Text strong style={{ color: token.colorPrimary }}>{formatPrice(previewCoupon.fromPrice)}</Text>
              </Descriptions.Item>
              <Descriptions.Item label="Скидка" span={1}>
                {previewCoupon.discountPercent ? `${previewCoupon.discountPercent}%` : '-'}
              </Descriptions.Item>
              <Descriptions.Item label="Статус" span={1}>
                <Tag color="purple">{previewCoupon.status}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Покупка до" span={1}>
                {formatDate(previewCoupon.buyUntil)}
              </Descriptions.Item>
              <Descriptions.Item label="Использование до" span={1}>
                {formatDate(previewCoupon.useUntil)}
              </Descriptions.Item>
            </Descriptions>

            <Divider titlePlacement="left">О мерчанте</Divider>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="Название">
                {previewCoupon.merchant?.name ?? 'Не указан'}
              </Descriptions.Item>
              <Descriptions.Item label="Описание">
                {previewCoupon.merchant?.description?.trim() || 'Не указано'}
              </Descriptions.Item>
              <Descriptions.Item label="Логотип">
                {previewCoupon.merchant?.logoUrl ? (
                  <Image
                    width={88}
                    src={previewCoupon.merchant.logoUrl}
                    alt={previewCoupon.merchant.name}
                    style={{ borderRadius: 8 }}
                    fallback="data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iODgiIGhlaWdodD0iODgiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9Ijg4IiBoZWlnaHQ9Ijg4IiBmaWxsPSIjZjVmNWY1Ii8+PHRleHQgeD0iNTAlIiB5PSI1MCUiIGZpbGw9IiNiYmIiIGRvbWluYW50LWJhc2VsaW5lPSJtaWRkbGUiIHRleHQtYW5jaG9yPSJtaWRkbGUiPkxvZ288L3RleHQ+PC9zdmc+"
                  />
                ) : (
                  'Не указан'
                )}
              </Descriptions.Item>
            </Descriptions>

            <Divider titlePlacement="left">Описание предложения</Divider>
            {previewCoupon.offerDescription ? (
              <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{previewCoupon.offerDescription}</Paragraph>
            ) : (
              <Alert
                type="warning"
                showIcon
                message="У купона не заполнено canonical описание оффера"
              />
            )}

            {/* Контактная информация — canonical only: merchant.primaryLocation */}
            <Divider titlePlacement="left">Контакты</Divider>
            <Descriptions bordered column={1} size="small">
              <Descriptions.Item label="Адрес">
                {previewCoupon.merchant?.primaryLocation?.address?.trim() || 'Не указан'}
              </Descriptions.Item>
              <Descriptions.Item label="Телефон">
                {previewCoupon.merchant?.primaryLocation?.phone?.trim() || 'Не указан'}
              </Descriptions.Item>
              <Descriptions.Item label="Время работы">
                {previewCoupon.merchant?.primaryLocation?.workingHours?.trim() || 'Не указано'}
              </Descriptions.Item>
            </Descriptions>

            {/* Варианты (сертификаты) */}
            {previewCoupon.options && previewCoupon.options.length > 0 && (
              <>
                <Divider titlePlacement="left">Варианты покупки ({previewCoupon.options.length})</Divider>
                <Row gutter={[8, 8]}>
                  {previewCoupon.options.map((opt) => (
                    <Col key={opt.id} xs={24} sm={12}>
                      <Card size="small">
                        <Text strong>{opt.title}</Text>
                        <div>
                          <Text delete type="secondary">{formatPrice(opt.regularPrice)}</Text>
                          {' → '}
                          <Text strong style={{ color: token.colorPrimary }}>{formatPrice(opt.couponPrice)}</Text>
                        </div>
                        <Text type="secondary">Лимит: {opt.quantityLimit}, продано: {opt.quantitySold}</Text>
                      </Card>
                    </Col>
                  ))}
                </Row>
              </>
            )}

            {/* Галерея */}
            {previewCoupon.images && previewCoupon.images.length > 0 && (
              <>
                <Divider titlePlacement="left">Галерея ({previewCoupon.images.length})</Divider>
                <Image.PreviewGroup>
                  <Space wrap>
                    {previewCoupon.images.map((url, idx) => (
                      <Image
                        key={idx}
                        src={url}
                        width={120}
                        height={80}
                        style={{ objectFit: 'cover', borderRadius: 4 }}
                      />
                    ))}
                  </Space>
                </Image.PreviewGroup>
              </>
            )}
          </div>
        )}
      </Modal>

      {/* ===== Модалка отклонения ===== */}
      <Modal
        title={<Space><MessageOutlined /><span>Причина отклонения</span></Space>}
        open={rejectModalOpen}
        onCancel={() => setRejectModalOpen(false)}
        onOk={handleRejectConfirm}
        confirmLoading={rejectMutation.isPending}
        okText="Отклонить"
        okButtonProps={{ danger: true }}
        cancelText="Отмена"
      >
        <Alert
          type="warning"
          showIcon
          message="Этот комментарий увидит модератор"
          description="Опишите, что нужно исправить."
          style={{ marginBottom: 16 }}
        />
        <TextArea
          rows={4}
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="Например: Неправильная цена, нужно уточнить условия..."
        />
      </Modal>
    </div>
  );
};
