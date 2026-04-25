import { App, Alert, Button, Card, Col, Empty, Row, Space, Spin, Tag, Typography, theme } from 'antd';
import {
  ClockCircleOutlined,
  EditOutlined,
  InboxOutlined,
  MessageOutlined,
  SendOutlined,
  ShopOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import type { AxiosError } from 'axios';
import api from '../../api/client';
import { useAuthStore } from '../../store/authStore';
import { getPublicationReadiness } from './publicationReadiness';

const { Paragraph, Text, Title } = Typography;

type CouponStatus =
  | 'LEAD'
  | 'DRAFT'
  | 'WAITING_FOR_MERCHANT'
  | 'REVISION_REQUESTED'
  | 'ACTIVE'
  | 'SOLD_OUT'
  | 'ARCHIVED';

interface MerchantSummary {
  id: number;
  name: string;
  primaryLocation?: {
    address?: string;
    active?: boolean;
  } | null;
}

interface Coupon {
  id: number;
  title: string;
  merchant: MerchantSummary | null;
  fromPrice: number;
  oldPrice: number | null;
  discountPercent: number | null;
  status: CouponStatus;
  revisionComment: string | null;
  assignedModeratorId: number | null;
  assignedModeratorName: string | null;
}

interface CouponsPagePayload {
  content: Coupon[];
}

interface ApiResponse<T> {
  data: T;
  message?: string | null;
  success?: boolean;
}

interface ApiErrorResponse {
  message?: string;
}


interface KanbanColumnConfig {
  key: Exclude<CouponStatus, 'ACTIVE'>;
  title: string;
  color: string;
  emptyText: string;
}

const KANBAN_COLUMNS: KanbanColumnConfig[] = [
  {
    key: 'LEAD',
    title: 'Новые',
    color: 'blue',
    emptyText: 'Новых купонов нет',
  },
  {
    key: 'DRAFT',
    title: 'В работе',
    color: 'gold',
    emptyText: 'Нет купонов в работе',
  },
  {
    key: 'WAITING_FOR_MERCHANT',
    title: 'Ожидают ответа',
    color: 'purple',
    emptyText: 'Мерчантам ничего не отправлено',
  },
  {
    key: 'REVISION_REQUESTED',
    title: 'Требуют правок',
    color: 'red',
    emptyText: 'Правки не запрошены',
  },
];

const statusTagColors: Record<CouponStatus, string> = {
  LEAD: 'blue',
  DRAFT: 'gold',
  WAITING_FOR_MERCHANT: 'purple',
  REVISION_REQUESTED: 'red',
  ACTIVE: 'green',
  SOLD_OUT: 'cyan',
  ARCHIVED: 'default',
};

const statusLabels: Record<CouponStatus, string> = {
  LEAD: 'LEAD',
  DRAFT: 'DRAFT',
  WAITING_FOR_MERCHANT: 'WAITING_FOR_MERCHANT',
  REVISION_REQUESTED: 'REVISION_REQUESTED',
  ACTIVE: 'ACTIVE',
  SOLD_OUT: 'SOLD_OUT',
  ARCHIVED: 'ARCHIVED',
};

function normalizeCoupons(data: Coupon[] | CouponsPagePayload): Coupon[] {
  return Array.isArray(data) ? data : data.content;
}

function getErrorMessage(error: unknown, fallback: string): string {
  const axiosError = error as AxiosError<ApiErrorResponse>;
  return axiosError.response?.data?.message ?? fallback;
}

function formatPrice(value: number | null | undefined): string {
  if (typeof value !== 'number') {
    return '-';
  }

  return `${value.toLocaleString('ru-RU')} сум`;
}

function getCouponsByStatus(coupons: Coupon[], status: KanbanColumnConfig['key']): Coupon[] {
  return coupons.filter((coupon) => coupon.status === status);
}

export const CouponKanbanPage = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { message } = App.useApp();
  const { token } = theme.useToken();
  const currentUserId = useAuthStore((s) => s.user?.id);
  const currentUserRole = useAuthStore((s) => s.user?.role);

  const { data: coupons = [], isLoading, isError } = useQuery({
    queryKey: ['admin-coupons', 'kanban'],
    queryFn: async () => {
      const res = await api.get<ApiResponse<Coupon[] | CouponsPagePayload>>('/api/v1/admin/coupons', { params: { size: 500 } });
      return normalizeCoupons(res.data.data).filter((coupon) => coupon.status !== 'ACTIVE');
    },
  });

  const updateStatusMutation = useMutation({
    mutationFn: async (couponId: number) => {
      await api.patch(`/api/v1/admin/coupons/${couponId}/take-to-work`);
      return couponId;
    },
    onSuccess: () => {
      message.success('Купон взят в работу');
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
    },
    onError: (mutationError: unknown) => {
      message.error(getErrorMessage(mutationError, 'Не удалось обновить статус купона'));
    },
  });

  const sendToApprovalMutation = useMutation({
    mutationFn: async (couponId: number) => {
      await api.post(`/api/v1/admin/coupons/${couponId}/send-to-approval`);
      return couponId;
    },
    onSuccess: () => {
      message.success('Купон отправлен мерчанту на согласование');
      queryClient.invalidateQueries({ queryKey: ['admin-coupons'] });
    },
    onError: (mutationError: unknown) => {
      message.error(getErrorMessage(mutationError, 'Не удалось отправить купон мерчанту'));
    },
  });

  return (
    <div>
      {isError && (
        <Alert
          type="warning"
          showIcon
          closable
          message="Бэкенд не отвечает"
          description="Не удалось загрузить купоны. Доска пока пустая. Проверьте, запущен ли coupon-service."
          style={{ marginBottom: 16 }}
        />
      )}

      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 24,
        }}
      >
        <div>
          <Title level={4} style={{ margin: 0 }}>
            Канбан купонов
          </Title>
          <Text type="secondary">
            Консьерж-сервис: модератор забирает лид, готовит карточку и отправляет мерчанту на согласование.
          </Text>
        </div>
        <Space>
          {isLoading && <Spin size="small" />}
          <Tag color="default">Всего на доске: {coupons.length}</Tag>
        </Space>
      </div>

      <Row gutter={16} align="top">
        {KANBAN_COLUMNS.map((column) => {
          const columnCoupons = getCouponsByStatus(coupons, column.key);

          return (
            <Col key={column.key} span={6}>
              <div
                style={{
                  background: token.colorBgLayout,
                  borderRadius: token.borderRadiusLG,
                  padding: 12,
                  height: 'calc(100vh - 260px)',
                  overflowY: 'auto',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: 12,
                    position: 'sticky',
                    top: 0,
                    background: token.colorBgLayout,
                    paddingBottom: 8,
                    zIndex: 1,
                  }}
                >
                  <Title level={5} style={{ margin: 0 }}>
                    {column.title}
                  </Title>
                  <Tag color={column.color}>{columnCoupons.length}</Tag>
                </div>

                <Space direction="vertical" size={12} style={{ width: '100%' }}>
                  {columnCoupons.length === 0 ? (
                    <Card size="small">
                      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={column.emptyText} />
                    </Card>
                  ) : (
                    columnCoupons.map((coupon) => (
                      <Card
                        key={coupon.id}
                        size="small"
                        title={
                          <Space size={8} wrap>
                            <Text strong>#{coupon.id}</Text>
                            <Tag color={statusTagColors[coupon.status]}>{statusLabels[coupon.status]}</Tag>
                          </Space>
                        }
                      >
                        <Space direction="vertical" size={10} style={{ width: '100%' }}>
                          <div>
                            <Paragraph strong style={{ marginBottom: 6 }}>
                              {coupon.title}
                            </Paragraph>
                            <Space size={6} wrap>
                              <ShopOutlined />
                              <Text>{coupon.merchant?.name ?? 'Мерчант не указан'}</Text>
                            </Space>
                          </div>

                          {(coupon.status === 'DRAFT' || coupon.status === 'REVISION_REQUESTED') && (() => {
                            const couponReadiness = getPublicationReadiness(coupon.merchant);
                            return !couponReadiness.ready ? (
                              <Alert
                                type="warning"
                                showIcon
                                title="Не готов к публикации"
                                description={couponReadiness.reasons.join('. ')}
                                style={{ marginBottom: 4 }}
                              />
                            ) : null;
                          })()}

                          <div>
                            <Text strong>{formatPrice(coupon.fromPrice)}</Text>
                            {coupon.oldPrice ? (
                              <Text type="secondary" delete style={{ marginLeft: 8 }}>
                                {formatPrice(coupon.oldPrice)}
                              </Text>
                            ) : null}
                          </div>

                          <Space size={8} wrap>
                            {typeof coupon.discountPercent === 'number' ? (
                              <Tag color="volcano">-{coupon.discountPercent}%</Tag>
                            ) : null}
                            <Tag color={statusTagColors[coupon.status]}>{column.title}</Tag>
                          </Space>

                          {coupon.status === 'REVISION_REQUESTED' && coupon.revisionComment ? (
                            <Alert
                              type="warning"
                              showIcon
                              icon={<MessageOutlined />}
                              message="Комментарий партнера"
                              description={coupon.revisionComment}
                            />
                          ) : null}

                          {coupon.status === 'WAITING_FOR_MERCHANT' ? (
                            <Alert
                              type="info"
                              showIcon
                              icon={<ClockCircleOutlined />}
                              message="Ожидаем ответ мерчанта"
                              description="Карточка временно заблокирована для модератора."
                            />
                          ) : null}

                          {(coupon.status === 'DRAFT' || coupon.status === 'REVISION_REQUESTED') && coupon.assignedModeratorName ? (
                            <div style={{ padding: '4px 0' }}>
                              <Text type="secondary" style={{ fontSize: 12 }}>
                                👤 {coupon.assignedModeratorName}
                              </Text>
                            </div>
                          ) : null}

                          {coupon.status === 'LEAD' ? (
                            <Button
                              type="primary"
                              icon={<InboxOutlined />}
                              block
                              loading={
                                updateStatusMutation.isPending &&
                                updateStatusMutation.variables === coupon.id
                              }
                              onClick={() => updateStatusMutation.mutate(coupon.id)}
                            >
                              Взять в работу
                            </Button>
                          ) : null}

                          {coupon.status === 'DRAFT' ? (() => {
                            const isOwner = !coupon.assignedModeratorId || coupon.assignedModeratorId === currentUserId || currentUserRole !== 'MODERATOR';
                            return (
                              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                                <Button
                                  icon={<EditOutlined />}
                                  block
                                  disabled={!isOwner}
                                  title={!isOwner ? `Закреплён за ${coupon.assignedModeratorName}` : undefined}
                                  onClick={() => navigate(`/moderation/coupons/edit/${coupon.id}`)}
                                >
                                  Редактировать
                                </Button>
                                <Button
                                  type="primary"
                                  icon={<SendOutlined />}
                                  block
                                  disabled={!isOwner}
                                  title={!isOwner ? `Закреплён за ${coupon.assignedModeratorName}` : undefined}
                                  loading={
                                    sendToApprovalMutation.isPending &&
                                    sendToApprovalMutation.variables === coupon.id
                                  }
                                  onClick={() => sendToApprovalMutation.mutate(coupon.id)}
                                >
                                  Отправить мерчанту
                                </Button>
                              </Space>
                            );
                          })() : null}

                          {coupon.status === 'REVISION_REQUESTED' ? (() => {
                            const isOwner = !coupon.assignedModeratorId || coupon.assignedModeratorId === currentUserId || currentUserRole !== 'MODERATOR';
                            return (
                              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                                <Button
                                  icon={<EditOutlined />}
                                  block
                                  disabled={!isOwner}
                                  title={!isOwner ? `Закреплён за ${coupon.assignedModeratorName}` : undefined}
                                  onClick={() => navigate(`/moderation/coupons/edit/${coupon.id}`)}
                                >
                                  Редактировать
                                </Button>
                                <Button
                                  type="primary"
                                  icon={<SendOutlined />}
                                  block
                                  disabled={!isOwner}
                                  title={!isOwner ? `Закреплён за ${coupon.assignedModeratorName}` : undefined}
                                  loading={
                                    sendToApprovalMutation.isPending &&
                                    sendToApprovalMutation.variables === coupon.id
                                  }
                                  onClick={() => sendToApprovalMutation.mutate(coupon.id)}
                                >
                                  Отправить снова
                                </Button>
                              </Space>
                            );
                          })() : null}
                        </Space>
                      </Card>
                    ))
                  )}
                </Space>
              </div>
            </Col>
          );
        })}
      </Row>
    </div>
  );
};
