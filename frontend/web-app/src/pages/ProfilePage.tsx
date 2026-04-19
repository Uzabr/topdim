import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LogOut, Settings, Clock, CheckCircle, Ticket, Wallet, AlertCircle } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { ordersApi } from '../api/orders';
import type { PurchasedCoupon } from '../api/orders';
import Tabs from '../components/ui/Tabs';
import { formatPrice } from '../utils/format';
import { useLocalePath } from '../hooks/useLocalePath';
import './ProfilePage.css';

export default function ProfilePage() {
  const { user, logout, isAuthenticated } = useAuthStore();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const [activeTab, setActiveTab] = useState('ACTIVE');

  // Fetch coupons from backend
  const { data: coupons = [], isLoading } = useQuery({
    queryKey: ['my-coupons', activeTab],
    queryFn: () => ordersApi.getMyCoupons(activeTab),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

  const PROFILE_TABS = [
    { key: 'ACTIVE', label: 'Активные', icon: <Clock size={16} /> },
    { key: 'USED', label: 'Использованные', icon: <CheckCircle size={16} /> },
    { key: 'EXPIRED', label: 'Истёкшие', icon: <AlertCircle size={16} /> },
  ];

  if (!isAuthenticated) {
    return (
      <div className="profile-page container">
        <div className="profile-empty glass-card">
          <Ticket size={48} className="profile-empty-icon" />
          <h2>Привет!</h2>
          <p>Войдите или зарегистрируйтесь, чтобы видеть свои купоны и историю возвратов.</p>
          <Link to={lp('/login')} className="primary-button">Войти в профиль</Link>
        </div>
      </div>
    );
  }

  const totalCouponsCount = coupons.length;
  // Сумма экономии будет считаться из реальных данных в будущем
  const savedAmount = 0;

  return (
    <div className="profile-page container">
      {/* ═══ Header Section ═══ */}
      <div className="profile-header glass-card">
        <div className="profile-header__top">
          <div className="profile-user">
            <div className="profile-avatar">
              {user?.firstName?.charAt(0)?.toUpperCase() || '?'}
            </div>
            <div className="profile-info">
              <h1>{user?.firstName} {user?.lastName}</h1>
              <p>{user?.email}{user?.phone ? ` • ${user.phone}` : ''}</p>
            </div>
          </div>
          
          <div className="profile-actions">
            <button className="icon-button" aria-label="Настройки">
              <Settings size={20} />
            </button>
            <button className="icon-button danger" aria-label="Выйти" onClick={logout}>
              <LogOut size={20} />
            </button>
          </div>
        </div>

        <div className="profile-stats">
          <div className="profile-stat-item">
            <div className="profile-stat-icon" style={{ background: 'rgba(255,102,96,0.1)', color: 'var(--primary)' }}>
              <Ticket size={24} />
            </div>
            <div className="profile-stat-data">
              <span className="profile-stat-label">Купонов куплено</span>
              <span className="profile-stat-value">{totalCouponsCount}</span>
            </div>
          </div>
          <div className="profile-stat-item">
            <div className="profile-stat-icon" style={{ background: 'rgba(52,199,89,0.1)', color: 'var(--success)' }}>
              <Wallet size={24} />
            </div>
            <div className="profile-stat-data">
              <span className="profile-stat-label">Сэкономлено</span>
              <span className="profile-stat-value">{savedAmount > 0 ? formatPrice(savedAmount) : '—'}</span>
            </div>
          </div>
        </div>
      </div>

      {/* ═══ Content Section ═══ */}
      <div className="profile-content">
        <h2 className="profile-section-title">Мои купоны</h2>
        
        <Tabs 
          tabs={PROFILE_TABS} 
          activeKey={activeTab} 
          onChange={setActiveTab} 
        />

        <div className="profile-coupons">
          {isLoading ? (
             <div className="profile-loading">Загрузка купонов...</div>
          ) : coupons.length === 0 ? (
            <div className="profile-coupons-empty glass-card">
              <span className="profile-empty-icon">😢</span>
              <h3>У вас пока нет {activeTab === 'ACTIVE' ? 'активных' : activeTab === 'USED' ? 'использованных' : 'истёкших'} купонов</h3>
              <p>Самое время порадовать себя отличной скидкой!</p>
              <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
                Перейти в каталог
              </button>
            </div>
          ) : (
            coupons.map((coupon: PurchasedCoupon) => (
               <div key={coupon.id} className={`purchased-coupon glass-card purchased-coupon--${coupon.status.toLowerCase()}`}>
                 <div className="purchased-coupon__main">
                   <h3>{coupon.couponTitle}</h3>
                   <p>{coupon.optionTitle}</p>
                 </div>
                 
                 <div className="purchased-coupon__details">
                    <div className="purchased-coupon__code">
                      <span className="code-label">ПИН КОД</span>
                      <span className="code-value">{coupon.couponCode || '—'}</span>
                    </div>
                    
                    <div className="purchased-coupon__meta">
                      <span className={`badge badge--${coupon.status.toLowerCase()}`}>
                        {coupon.status === 'ACTIVE' ? 'Активно' :
                         coupon.status === 'USED' ? 'Использовано' : 'Истёк'}
                      </span>
                      {coupon.expiresAt && (
                        <span className="coupon-expires">
                          {activeTab === 'USED' ? 'Использован: ' : 'Действует до '} 
                          {new Date(coupon.expiresAt).toLocaleDateString('ru-RU')}
                        </span>
                      )}
                    </div>
                 </div>

                 {coupon.status === 'ACTIVE' && coupon.couponCode && (
                    <button 
                      className="primary-button purchased-coupon__btn"
                      onClick={() => {
                        navigator.clipboard.writeText(coupon.couponCode);
                        alert('ПИН-код скопирован!');
                      }}
                    >
                      Скопировать ПИН-код
                    </button>
                 )}
               </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
