import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Alert,
  Button,
  Card,
  Col,
  Empty,
  List,
  Row,
  Skeleton,
  Space,
  Statistic,
  Tag,
  Typography,
} from 'antd';
import {
  AlertOutlined,
  ClockCircleOutlined,
  ReloadOutlined,
  ShoppingCartOutlined,
  TagOutlined,
  TeamOutlined,
} from '@ant-design/icons';
import { Link } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import {
  fetchAdminDashboard,
  fetchAdminUserCount,
  fetchCouponCount,
  fetchPendingComplaintCount,
  type DailySales,
} from './api';

const { Text, Title } = Typography;

const currencyFormatter = new Intl.NumberFormat('ru-RU', {
  maximumFractionDigits: 0,
});

const shortDateFormatter = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  timeZone: 'Asia/Tashkent',
});

const dateTimeFormatter = new Intl.DateTimeFormat('ru-RU', {
  day: '2-digit',
  month: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  timeZone: 'Asia/Tashkent',
});

interface MetricCardProps {
  title: string;
  ariaTitle?: string;
  value?: number;
  suffix?: string;
  color: string;
  icon: ReactNode;
  loading: boolean;
  error: boolean;
  formatter?: (value: number) => string;
}

function MetricCard({
  title,
  ariaTitle = title,
  value,
  suffix,
  color,
  icon,
  loading,
  error,
  formatter,
}: MetricCardProps) {
  const visibleValue = value === undefined ? 0 : value;
  const renderedValue = formatter ? formatter(visibleValue) : visibleValue;
  const ariaValue = loading ? 'загрузка' : error ? 'недоступно' : renderedValue;

  return (
    <Col xs={24} sm={12} lg={8} xl={6}>
      <Card
        variant="borderless"
        style={{ borderLeft: `4px solid ${color}`, height: '100%' }}
        aria-label={`${ariaTitle}: ${ariaValue}`}
      >
        {loading ? (
          <Skeleton active paragraph={false} />
        ) : (
          <Statistic
            title={title}
            value={error ? '—' : renderedValue}
            suffix={error ? undefined : suffix}
            prefix={icon}
            styles={{ content: { color: error ? '#8c8c8c' : color } }}
          />
        )}
      </Card>
    </Col>
  );
}

function SalesChart({ sales }: { sales: DailySales[] }) {
  const maxRevenue = Math.max(...sales.map((day) => day.revenue), 1);

  return (
    <div
      role="region"
      aria-label="Продажи за последние 7 дней"
      style={{
        height: 280,
        display: 'flex',
        alignItems: 'stretch',
        gap: 12,
        paddingTop: 16,
      }}
    >
      {sales.map((day) => {
        const height = day.revenue === 0
          ? 2
          : Math.max(12, Math.round((day.revenue / maxRevenue) * 190));
        return (
          <div
            key={day.date}
            aria-label={`${day.date}: ${day.orders} заказов, ${currencyFormatter.format(day.revenue)} сум`}
            style={{
              flex: 1,
              minWidth: 0,
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'flex-end',
              alignItems: 'center',
              gap: 8,
            }}
          >
            <Text strong style={{ fontSize: 12, textAlign: 'center' }}>
              {currencyFormatter.format(day.revenue)}
            </Text>
            <div
              style={{
                width: 'min(44px, 80%)',
                height,
                borderRadius: '6px 6px 2px 2px',
                background: day.revenue === 0 ? '#d9d9d9' : '#1677ff',
              }}
            />
            <Text type="secondary" style={{ fontSize: 12 }}>
              {shortDateFormatter.format(new Date(`${day.date}T00:00:00+05:00`))}
            </Text>
          </div>
        );
      })}
    </div>
  );
}

function AdminDashboard() {
  const dashboardQuery = useQuery({
    queryKey: ['admin-dashboard', 'orders'],
    queryFn: fetchAdminDashboard,
  });
  const activeCouponsQuery = useQuery({
    queryKey: ['admin-dashboard', 'active-coupons'],
    queryFn: () => fetchCouponCount({ status: 'ACTIVE' }),
  });
  const usersQuery = useQuery({
    queryKey: ['admin-dashboard', 'users'],
    queryFn: fetchAdminUserCount,
  });

  return (
    <>
      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <MetricCard
          title="Заказы сегодня"
          value={dashboardQuery.data?.ordersToday}
          color="#1677ff"
          icon={<ShoppingCartOutlined />}
          loading={dashboardQuery.isLoading}
          error={dashboardQuery.isError}
        />
        <MetricCard
          title="Выручка сегодня"
          value={dashboardQuery.data?.paidRevenueToday}
          suffix="сум"
          color="#13a8a8"
          icon={<ShoppingCartOutlined />}
          loading={dashboardQuery.isLoading}
          error={dashboardQuery.isError}
          formatter={currencyFormatter.format}
        />
        <MetricCard
          title="Активных купонов"
          value={activeCouponsQuery.data}
          color="#52c41a"
          icon={<TagOutlined />}
          loading={activeCouponsQuery.isLoading}
          error={activeCouponsQuery.isError}
        />
        <MetricCard
          title="Пользователей"
          value={usersQuery.data}
          color="#722ed1"
          icon={<TeamOutlined />}
          loading={usersQuery.isLoading}
          error={usersQuery.isError}
        />
        <MetricCard
          title="Жалоб (ожидают)"
          ariaTitle="Жалоб ожидают"
          value={dashboardQuery.data?.pendingComplaints}
          color="#fa8c16"
          icon={<AlertOutlined />}
          loading={dashboardQuery.isLoading}
          error={dashboardQuery.isError}
        />
      </Row>

      {dashboardQuery.isError && (
        <Alert
          style={{ marginTop: 16 }}
          type="warning"
          showIcon
          message="Данные заказов временно недоступны"
          description="Купоны и пользователи загружаются независимо. Повторите запрос к order-service."
          action={(
            <Button
              size="small"
              icon={<ReloadOutlined />}
              aria-label="Повторить загрузку заказов"
              onClick={() => dashboardQuery.refetch()}
            >
              Повторить
            </Button>
          )}
        />
      )}

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} lg={16}>
          <Card title="Продажи за последние 7 дней" variant="borderless">
            {dashboardQuery.isLoading ? (
              <Skeleton active />
            ) : dashboardQuery.data ? (
              <SalesChart sales={dashboardQuery.data.salesLast7Days} />
            ) : (
              <Empty description="Нет данных о продажах" />
            )}
          </Card>
        </Col>
        <Col xs={24} lg={8}>
          <Card title="Последние заказы" variant="borderless" style={{ height: '100%' }}>
            {dashboardQuery.isLoading ? (
              <Skeleton active />
            ) : dashboardQuery.data?.recentOrders.length ? (
              <List
                dataSource={dashboardQuery.data.recentOrders}
                renderItem={(order) => (
                  <List.Item>
                    <List.Item.Meta
                      title={(
                        <Space>
                          <Text strong>{order.orderNumber}</Text>
                          <Tag>{order.status}</Tag>
                        </Space>
                      )}
                      description={(
                        <Space orientation="vertical" size={0}>
                          <Text type="secondary">{order.userEmail || 'Email не указан'}</Text>
                          <Text>
                            {currencyFormatter.format(order.totalAmount)} сум ·{' '}
                            {dateTimeFormatter.format(new Date(`${order.createdAt}+05:00`))}
                          </Text>
                        </Space>
                      )}
                    />
                  </List.Item>
                )}
              />
            ) : (
              <Empty description="Заказов пока нет" />
            )}
          </Card>
        </Col>
      </Row>
    </>
  );
}

function ModeratorDashboard({ userId }: { userId: number }) {
  const newCouponsQuery = useQuery({
    queryKey: ['moderator-dashboard', 'new-coupons'],
    queryFn: () => fetchCouponCount({ status: 'LEAD' }),
  });
  const myWorkQuery = useQuery({
    queryKey: ['moderator-dashboard', 'my-work', userId],
    queryFn: () => fetchCouponCount({
      statuses: 'DRAFT,REVISION_REQUESTED',
      assignedModeratorId: userId,
    }),
  });
  const waitingPartnerQuery = useQuery({
    queryKey: ['moderator-dashboard', 'waiting-partner', userId],
    queryFn: () => fetchCouponCount({
      status: 'WAITING_FOR_MERCHANT',
      assignedModeratorId: userId,
    }),
  });
  const complaintsQuery = useQuery({
    queryKey: ['moderator-dashboard', 'complaints'],
    queryFn: fetchPendingComplaintCount,
  });

  const queries = [newCouponsQuery, myWorkQuery, waitingPartnerQuery, complaintsQuery];
  const hasError = queries.some((query) => query.isError);
  const retryAll = () => queries.forEach((query) => query.refetch());

  return (
    <>
      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <MetricCard
          title="Новых купонов"
          value={newCouponsQuery.data}
          color="#1677ff"
          icon={<TagOutlined />}
          loading={newCouponsQuery.isLoading}
          error={newCouponsQuery.isError}
        />
        <MetricCard
          title="Мои купоны в работе"
          value={myWorkQuery.data}
          color="#722ed1"
          icon={<ClockCircleOutlined />}
          loading={myWorkQuery.isLoading}
          error={myWorkQuery.isError}
        />
        <MetricCard
          title="Ожидают партнёра"
          value={waitingPartnerQuery.data}
          color="#13a8a8"
          icon={<TeamOutlined />}
          loading={waitingPartnerQuery.isLoading}
          error={waitingPartnerQuery.isError}
        />
        <MetricCard
          title="Жалоб (ожидают)"
          ariaTitle="Жалоб ожидают"
          value={complaintsQuery.data}
          color="#fa8c16"
          icon={<AlertOutlined />}
          loading={complaintsQuery.isLoading}
          error={complaintsQuery.isError}
        />
      </Row>

      {hasError && (
        <Alert
          style={{ marginTop: 16 }}
          type="warning"
          showIcon
          message="Часть очередей временно недоступна"
          action={(
            <Button size="small" icon={<ReloadOutlined />} onClick={retryAll}>
              Повторить
            </Button>
          )}
        />
      )}

      <Row gutter={[16, 16]} style={{ marginTop: 24 }}>
        <Col xs={24} md={12}>
          <Card title="Рабочая очередь" variant="borderless">
            <Space orientation="vertical" size="middle">
              <Text>
                Сначала возьмите новый купон в работу, затем заполните карточку и отправьте её партнёру.
              </Text>
              <Link to="/coupons?tab=new&view=table&page=0&size=20">
                Открыть новые купоны
              </Link>
              <Link to={`/coupons?tab=in-progress&view=table&assignedModeratorId=${userId}&page=0&size=20`}>
                Открыть мои купоны
              </Link>
            </Space>
          </Card>
        </Col>
        <Col xs={24} md={12}>
          <Card title="Поддержка" variant="borderless">
            <Space orientation="vertical" size="middle">
              <Text>
                Обрабатывайте жалобы и отзывы только в рамках доступных модератору действий.
              </Text>
              <Link to="/support/complaints">Открыть жалобы</Link>
              <Link to="/support/reviews">Открыть отзывы</Link>
            </Space>
          </Card>
        </Col>
      </Row>
    </>
  );
}

export const DashboardPage = () => {
  const user = useAuthStore((state) => state.user);

  if (!user) {
    return <Alert type="error" showIcon message="Не удалось определить пользователя" />;
  }

  return (
    <div>
      <Title level={4}>Добро пожаловать, {user.firstName}! 👋</Title>
      {user.role === 'MODERATOR'
        ? <ModeratorDashboard userId={user.id} />
        : <AdminDashboard />}
    </div>
  );
};
