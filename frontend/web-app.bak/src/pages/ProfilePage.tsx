import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Ticket, LogOut } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { ordersApi } from '../api/orders';
import type { PurchasedCoupon } from '../api/orders';
import './ProfilePage.css';

const TABS = [
  { key: 'ACTIVE', label: 'Активные' },
  { key: 'USED', label: 'Использованные' },
  { key: 'EXPIRED', label: 'Истёкшие' },
] as const;

export default function ProfilePage() {
  const { user, logout, isAuthenticated } = useAuthStore();
  const [activeTab, setActiveTab] = useState('ACTIVE');

  const { data: coupons = [] } = useQuery({
    queryKey: ['my-coupons', activeTab],
    queryFn: () => ordersApi.getMyCoupons(activeTab),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

  if (!isAuthenticated) {
    return (
      <div className="profile-page container">
        <div className="profile-empty">
          <p>Войдите, чтобы видеть свои купоны</p>
        </div>
      </div>
    );
  }

  return (
    <div className="profile-page container">
      <div className="profile-header">
        <div className="profile-avatar">
          {user?.firstName?.charAt(0)?.toUpperCase() || '?'}
        </div>
        <div className="profile-info">
          <h1>{user?.firstName} {user?.lastName}</h1>
          <p>{user?.email}</p>
        </div>
        <button className="profile-logout" onClick={logout}>
          <LogOut size={18} />
          Выйти
        </button>
      </div>

      <h2 className="profile-section-title">
        <Ticket size={20} />
        Мои купоны
      </h2>

      <div className="profile-tabs">
        {TABS.map((tab) => (
          <button
            key={tab.key}
            className={`profile-tab ${activeTab === tab.key ? 'profile-tab--active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="purchased-coupons">
        {coupons.length === 0 ? (
          <div className="coupons-empty">
            <span>🎫</span>
            <p>Нет купонов</p>
          </div>
        ) : (
          coupons.map((coupon: PurchasedCoupon) => (
            <div key={coupon.id} className={`purchased-coupon purchased-coupon--${coupon.status.toLowerCase()}`}>
              <div className="purchased-coupon__main">
                <h3>{coupon.couponTitle}</h3>
                <p>{coupon.optionTitle}</p>
              </div>
              <div className="purchased-coupon__code">
                <span className="code-label">Код:</span>
                <span className="code-value">{coupon.couponCode}</span>
              </div>
              <div className="purchased-coupon__meta">
                <span className={`coupon-status coupon-status--${coupon.status.toLowerCase()}`}>
                  {coupon.status === 'ACTIVE' ? '✅ Активен' :
                   coupon.status === 'USED' ? '✓ Использован' : '⏰ Истёк'}
                </span>
                {coupon.expiresAt && (
                  <span className="coupon-expires">
                    до {new Date(coupon.expiresAt).toLocaleDateString('ru-RU')}
                  </span>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
