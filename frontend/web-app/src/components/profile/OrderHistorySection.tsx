import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { ShoppingBag, CreditCard, Clock, CheckCircle, XCircle, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { ordersApi } from '../../api/orders';
import type { OrderResponse } from '../../api/orders';
import { useAuthStore } from '../../store/authStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import { formatPrice, formatDate } from '../../utils/format';
import type { ProfileTab } from '../../pages/ProfilePage';
import './OrderHistorySection.css';

const ORDER_STATUS_ICONS: Record<string, React.ReactNode> = {
  PENDING: <Clock size={16} />,
  PAID: <CheckCircle size={16} />,
  COMPLETED: <CheckCircle size={16} />,
  CANCELLED: <XCircle size={16} />,
  REFUND_REQUESTED: <RefreshCw size={16} />,
  REFUNDED: <RefreshCw size={16} />,
};

interface OrderHistorySectionProps {
  onTabChange: (tab: ProfileTab) => void;
}

export default function OrderHistorySection({ onTabChange }: OrderHistorySectionProps) {
  const { t } = useTranslation();
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();
  const lp = useLocalePath();

  const { data: ordersData, isLoading, isError } = useQuery({
    queryKey: ['my-orders'],
    queryFn: () => ordersApi.getOrders(0, 20),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

  const orders = ordersData?.content ?? [];

  if (isLoading) {
    return <div className="orders-loading">{t('profile.ordersSection.loading')}</div>;
  }

  if (isError) {
    return (
      <div className="orders-error glass-card">
        <XCircle size={24} />
        <p>{t('profile.ordersSection.error')}</p>
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="orders-empty glass-card">
        <ShoppingBag size={40} className="orders-empty__icon" />
        <h3>{t('profile.ordersSection.emptyTitle')}</h3>
        <p>{t('profile.ordersSection.emptyDesc')}</p>
        <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
          {t('profile.goCatalog')}
        </button>
      </div>
    );
  }

  return (
    <div className="orders-list">
      {orders.map((order) => (
        <OrderCard key={order.id} order={order} onTabChange={onTabChange} />
      ))}
    </div>
  );
}

function OrderCard({ order, onTabChange }: { order: OrderResponse; onTabChange: (tab: ProfileTab) => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();

  const statusLabels = useMemo(() => ({
    PENDING: t('profile.orderStatus.pending'),
    PAID: t('profile.orderStatus.paid'),
    COMPLETED: t('profile.orderStatus.completed'),
    CANCELLED: t('profile.orderStatus.cancelled'),
    REFUND_REQUESTED: t('profile.orderStatus.refundRequested'),
    REFUNDED: t('profile.orderStatus.refunded'),
  }), [t]);

  const statusLabel = statusLabels[order.status as keyof typeof statusLabels] ?? order.status;
  const statusIcon = ORDER_STATUS_ICONS[order.status] ?? <Clock size={16} />;
  const statusClass = `order-card__status--${order.status.toLowerCase()}`;

  return (
    <div className="order-card glass-card">
      <div className="order-card__header">
        <div className="order-card__number">
          <CreditCard size={16} />
          <span>{t('profile.ordersSection.orderNumber', { number: order.orderNumber || order.id })}</span>
        </div>
        <div className={`order-card__status ${statusClass}`}>
          {statusIcon}
          {statusLabel}
        </div>
      </div>
      <div className="order-card__body">
        <div className="order-card__detail">
          <span className="order-card__label">{t('profile.order.amount')}</span>
          <span className="order-card__value">{formatPrice(order.totalAmount)}</span>
        </div>
        <div className="order-card__detail">
          <span className="order-card__label">{t('profile.order.created')}</span>
          <span className="order-card__value">{formatDate(order.createdAt)}</span>
        </div>
        {order.paidAt && (
          <div className="order-card__detail">
            <span className="order-card__label">{t('profile.order.paid')}</span>
            <span className="order-card__value">{formatDate(order.paidAt)}</span>
          </div>
        )}
        <div className="order-card__detail">
          <span className="order-card__label">{t('profile.order.items')}</span>
          <span className="order-card__value">{order.itemCount}</span>
        </div>
      </div>
      <div className="order-card__actions">
        {order.status === 'PENDING' && (
          <button
            className="primary-button order-card__btn"
            onClick={() => navigate(lp(`/payment/${order.id}`))}
            id={`continue-payment-${order.id}`}
          >
            {t('profile.ordersSection.continuePay')}
          </button>
        )}
        {(order.status === 'PAID' || order.status === 'COMPLETED') && (
          <button
            className="secondary-button order-card__btn"
            onClick={() => onTabChange('coupons')}
          >
            {t('profile.ordersSection.viewCoupons')}
          </button>
        )}
      </div>
    </div>
  );
}
