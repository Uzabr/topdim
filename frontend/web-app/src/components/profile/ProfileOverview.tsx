import { Package, ShoppingBag, CheckCircle } from 'lucide-react';
import { useAuthStore } from '../../store/authStore';
import type { ProfileTab } from '../../pages/ProfilePage';
import './ProfileOverview.css';

interface ProfileOverviewProps {
  activeCouponsCount: number;
  usedCouponsCount: number;
  ordersCount: number;
  onTabChange: (tab: ProfileTab) => void;
}

export default function ProfileOverview({
  activeCouponsCount,
  usedCouponsCount,
  ordersCount,
  onTabChange,
}: ProfileOverviewProps) {
  const { user } = useAuthStore();

  const hasPhone = !!user?.phone;

  return (
    <div className="profile-overview glass-card">
      <div className="profile-overview__user">
        <div className="profile-overview__avatar">
          {user?.firstName?.charAt(0)?.toUpperCase() || '?'}
        </div>
        <div className="profile-overview__info">
          <h2 className="profile-overview__name">
            {user?.firstName} {user?.lastName}
          </h2>
          <p className="profile-overview__email">{user?.email}</p>
          <p className="profile-overview__phone">
            {user?.phone || 'Телефон не указан'}
          </p>
        </div>
      </div>

      {!hasPhone && (
        <div className="profile-overview__warning">
          <span>⚠️</span>
          <div>
            <p>Телефон нужен для оформления заказа и связи по купону.</p>
            <button
              className="profile-overview__warning-link"
              onClick={() => onTabChange('profile')}
            >
              Заполнить телефон →
            </button>
          </div>
        </div>
      )}

      <div className="profile-overview__stats">
        <button className="profile-overview__stat" onClick={() => onTabChange('coupons')}>
          <Package size={20} />
          <span className="profile-overview__stat-value">{activeCouponsCount}</span>
          <span className="profile-overview__stat-label">Активных</span>
        </button>
        <button className="profile-overview__stat" onClick={() => onTabChange('coupons')}>
          <CheckCircle size={20} />
          <span className="profile-overview__stat-value">{usedCouponsCount}</span>
          <span className="profile-overview__stat-label">Использовано</span>
        </button>
        <button className="profile-overview__stat" onClick={() => onTabChange('orders')}>
          <ShoppingBag size={20} />
          <span className="profile-overview__stat-value">{ordersCount}</span>
          <span className="profile-overview__stat-label">Заказов</span>
        </button>
      </div>
    </div>
  );
}
