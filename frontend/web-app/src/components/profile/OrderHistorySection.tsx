import { useQuery } from '@tanstack/react-query';
import { ShoppingBag, CreditCard, Clock, CheckCircle, XCircle, RefreshCw } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { ordersApi } from '../../api/orders';
import type { OrderResponse } from '../../api/orders';
import { useAuthStore } from '../../store/authStore';
import { useLocalePath } from '../../hooks/useLocalePath';
import { formatPrice, formatDate } from '../../utils/format';
import type { ProfileTab } from '../../pages/ProfilePage';
import './OrderHistorySection.css';

const ORDER_STATUS_LABELS: Record<string, string> = {
  PENDING: 'Ожидает оплаты',
  PAID: 'Оплачен',
  COMPLETED: 'Завершён',
  CANCELLED: 'Отменён',
  REFUND_REQUESTED: 'Запрошен возврат',
  REFUNDED: 'Возвращён',
};

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
    return <div className="orders-loading">Загрузка заказов...</div>;
  }

  if (isError) {
    return (
      <div className="orders-error glass-card">
        <XCircle size={24} />
        <p>Не удалось загрузить заказы. Попробуйте обновить страницу.</p>
      </div>
    );
  }

  if (orders.length === 0) {
    return (
      <div className="orders-empty glass-card">
        <ShoppingBag size={40} className="orders-empty__icon" />
        <h3>У вас пока нет заказов</h3>
        <p>Самое время купить первый купон!</p>
        <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
          Перейти в каталог
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
  const navigate = useNavigate();
  const lp = useLocalePath();
  const statusLabel = ORDER_STATUS_LABELS[order.status] ?? order.status;
  const statusIcon = ORDER_STATUS_ICONS[order.status] ?? <Clock size={16} />;
  const statusClass = `order-card__status--${order.status.toLowerCase()}`;

  return (
    <div className="order-card glass-card">
      <div className="order-card__header">
        <div className="order-card__number">
          <CreditCard size={16} />
          <span>Заказ #{order.orderNumber || order.id}</span>
        </div>
        <div className={`order-card__status ${statusClass}`}>
          {statusIcon}
          {statusLabel}
        </div>
      </div>
      <div className="order-card__body">
        <div className="order-card__detail">
          <span className="order-card__label">Сумма</span>
          <span className="order-card__value">{formatPrice(order.totalAmount)}</span>
        </div>
        <div className="order-card__detail">
          <span className="order-card__label">Создан</span>
          <span className="order-card__value">{formatDate(order.createdAt)}</span>
        </div>
        {order.paidAt && (
          <div className="order-card__detail">
            <span className="order-card__label">Оплачен</span>
            <span className="order-card__value">{formatDate(order.paidAt)}</span>
          </div>
        )}
        <div className="order-card__detail">
          <span className="order-card__label">Позиций</span>
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
            Продолжить оплату
          </button>
        )}
        {(order.status === 'PAID' || order.status === 'COMPLETED') && (
          <button
            className="secondary-button order-card__btn"
            onClick={() => onTabChange('coupons')}
          >
            Мои купоны
          </button>
        )}
      </div>
    </div>
  );
}
