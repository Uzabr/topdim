import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { LogOut, Clock, CheckCircle, Ticket, AlertCircle, ShoppingBag, Settings, HelpCircle, RotateCcw, MessageSquare, Bell } from 'lucide-react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuthStore } from '../store/authStore';
import { ordersApi } from '../api/orders';
import type { PurchasedCoupon } from '../api/orders';
import { notificationsApi } from '../api/notifications';
import Tabs from '../components/ui/Tabs';
import PurchasedCouponCard from '../components/profile/PurchasedCouponCard';
import ProfileOverview from '../components/profile/ProfileOverview';
import OrderHistorySection from '../components/profile/OrderHistorySection';
import ProfileSettingsSection from '../components/profile/ProfileSettingsSection';
import ProfileHelpSection from '../components/profile/ProfileHelpSection';
import RefundsSection from '../components/profile/RefundsSection';
import ComplaintsSection from '../components/profile/ComplaintsSection';
import NotificationsSection from '../components/profile/NotificationsSection';
import RefundRequestModal from '../components/profile/RefundRequestModal';
import ComplaintModal from '../components/profile/ComplaintModal';
import { useLocalePath } from '../hooks/useLocalePath';
import './ProfilePage.css';

export type ProfileTab = 'coupons' | 'orders' | 'profile' | 'help' | 'refunds' | 'complaints' | 'notifications';

function getInitialTab(search: string): ProfileTab {
  const tab = new URLSearchParams(search).get('tab');
  if (['orders', 'profile', 'help', 'refunds', 'complaints', 'notifications'].includes(tab || '')) return tab as ProfileTab;
  return 'coupons';
}

const SIDEBAR_ITEMS: { key: ProfileTab; label: string; icon: React.ReactNode }[] = [
  { key: 'coupons', label: 'Мои купоны', icon: <Ticket size={18} /> },
  { key: 'orders', label: 'Мои заказы', icon: <ShoppingBag size={18} /> },
  { key: 'refunds', label: 'Возвраты', icon: <RotateCcw size={18} /> },
  { key: 'complaints', label: 'Обращения', icon: <MessageSquare size={18} /> },
  { key: 'notifications', label: 'Уведомления', icon: <Bell size={18} /> },
  { key: 'profile', label: 'Настройки', icon: <Settings size={18} /> },
  { key: 'help', label: 'Помощь', icon: <HelpCircle size={18} /> },
];

const COUPON_TABS = [
  { key: 'ACTIVE', label: 'Активные', icon: <Clock size={16} /> },
  { key: 'REFUND_PENDING', label: 'На возврате', icon: <RotateCcw size={16} /> },
  { key: 'USED', label: 'Использованные', icon: <CheckCircle size={16} /> },
  { key: 'EXPIRED', label: 'Истёкшие', icon: <AlertCircle size={16} /> },
  { key: 'REFUNDED', label: 'Возвращённые', icon: <RotateCcw size={16} /> },
];

const EMPTY_COUPON_COPY: Record<string, { title: string; text: string }> = {
  ACTIVE: {
    title: 'У вас пока нет активных купонов',
    text: 'Выберите предложение в каталоге и купон появится здесь после оплаты.',
  },
  REFUND_PENDING: {
    title: 'Нет купонов на возврате',
    text: 'Когда вы запросите возврат, его статус появится здесь.',
  },
  USED: {
    title: 'Пока нет использованных купонов',
    text: 'После визита к партнёру использованные купоны будут здесь.',
  },
  EXPIRED: {
    title: 'Нет истёкших купонов',
    text: 'Купоны с истёкшим сроком будут отображаться в этом разделе.',
  },
  REFUNDED: {
    title: 'Нет возвращённых купонов',
    text: 'Завершённые возвраты будут отображаться здесь.',
  },
};

export default function ProfilePage() {
  const { user, logout, isAuthenticated } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const lp = useLocalePath();

  const [activeHubTab, setActiveHubTab] = useState<ProfileTab>(() => getInitialTab(location.search));
  const [couponSubTab, setCouponSubTab] = useState('ACTIVE');

  // Modal state
  const [refundCoupon, setRefundCoupon] = useState<PurchasedCoupon | null>(null);
  const [complaintCoupon, setComplaintCoupon] = useState<PurchasedCoupon | null>(null);

  const setTab = (tab: ProfileTab) => {
    setActiveHubTab(tab);
    navigate(`${location.pathname}?tab=${tab}`, { replace: true });
  };

  // Fetch coupons
  const { data: coupons = [], isLoading: couponsLoading } = useQuery({
    queryKey: ['my-coupons', couponSubTab],
    queryFn: () => ordersApi.getMyCoupons(couponSubTab),
    select: (res) => res.data.data,
    enabled: isAuthenticated && activeHubTab === 'coupons',
  });

  // Unread notifications badge
  const { data: hasUnread = false } = useQuery({
    queryKey: ['unread-notifications-badge'],
    queryFn: () => notificationsApi.getMine(true, 0, 1),
    select: (res) => (res.data.data?.totalElements ?? 0) > 0,
    enabled: isAuthenticated,
    staleTime: 60_000,
    retry: false,
  });

  // Fetch active + used counts for overview
  const { data: activeCoupons = [] } = useQuery({
    queryKey: ['my-coupons', 'ACTIVE'],
    queryFn: () => ordersApi.getMyCoupons('ACTIVE'),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

  const { data: usedCoupons = [] } = useQuery({
    queryKey: ['my-coupons', 'USED'],
    queryFn: () => ordersApi.getMyCoupons('USED'),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

  // Orders count for overview
  const { data: ordersData } = useQuery({
    queryKey: ['my-orders'],
    queryFn: () => ordersApi.getOrders(0, 1),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

  if (!isAuthenticated) {
    return (
      <div className="profile-page container">
        <div className="profile-empty glass-card">
          <Ticket size={48} className="profile-empty-icon" />
          <h2>Привет!</h2>
          <p>Войдите или зарегистрируйтесь, чтобы видеть купоны, заказы и статус оплаты.</p>
          <Link to={lp('/login')} className="primary-button">Войти в профиль</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="profile-page container">
      {/* ═══ Two-column Layout ═══ */}
      <div className="profile-layout">
        {/* ═══ Main Content (left) ═══ */}
        <div className="profile-main">
          {/* Overview card */}
          <ProfileOverview
            activeCouponsCount={activeCoupons.length}
            usedCouponsCount={usedCoupons.length}
            ordersCount={ordersData?.totalElements ?? 0}
            onTabChange={setTab}
          />

          {/* Tab Content */}
          <div className="profile-content">
            {activeHubTab === 'coupons' && (
              <>
                <h2 className="profile-section-title">Мои купоны</h2>
                <Tabs
                  tabs={COUPON_TABS}
                  activeKey={couponSubTab}
                  onChange={setCouponSubTab}
                />
                <div className="profile-coupons">
                  {couponsLoading ? (
                    <div className="profile-loading">Загрузка купонов...</div>
                  ) : coupons.length === 0 ? (
                    <div className="profile-coupons-empty glass-card">
                      <span className="profile-empty-icon">📋</span>
                      <h3>{EMPTY_COUPON_COPY[couponSubTab]?.title || 'Нет купонов'}</h3>
                      <p>{EMPTY_COUPON_COPY[couponSubTab]?.text || 'Купоны появятся здесь.'}</p>
                      {couponSubTab === 'ACTIVE' && (
                        <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
                          Перейти в каталог
                        </button>
                      )}
                    </div>
                  ) : (
                    coupons.map((coupon) => (
                      <PurchasedCouponCard
                        key={coupon.id}
                        coupon={coupon}
                        onRefundRequest={setRefundCoupon}
                        onComplaintRequest={setComplaintCoupon}
                      />
                    ))
                  )}
                </div>
              </>
            )}

            {activeHubTab === 'orders' && (
              <>
                <h2 className="profile-section-title">Мои заказы</h2>
                <OrderHistorySection onTabChange={setTab} />
              </>
            )}

            {activeHubTab === 'refunds' && (
              <>
                <h2 className="profile-section-title">Возвраты</h2>
                <RefundsSection />
              </>
            )}

            {activeHubTab === 'complaints' && (
              <>
                <h2 className="profile-section-title">Обращения</h2>
                <ComplaintsSection />
              </>
            )}

            {activeHubTab === 'notifications' && (
              <>
                <h2 className="profile-section-title">Уведомления</h2>
                <NotificationsSection />
              </>
            )}

            {activeHubTab === 'profile' && (
              <ProfileSettingsSection />
            )}

            {activeHubTab === 'help' && (
              <ProfileHelpSection />
            )}
          </div>
        </div>

        {/* ═══ Sidebar (right) ═══ */}
        <aside className="profile-sidebar">
          <div className="profile-sidebar__card glass-card">
            {/* User mini card */}
            <div className="profile-sidebar__user">
              <div className="profile-sidebar__avatar">
                {user?.firstName?.charAt(0)?.toUpperCase() || '?'}
              </div>
              <div className="profile-sidebar__user-info">
                <span className="profile-sidebar__name">{user?.firstName} {user?.lastName}</span>
                <span className="profile-sidebar__email">{user?.email}</span>
              </div>
            </div>

            {/* Nav items */}
            <nav className="profile-sidebar__nav">
              {SIDEBAR_ITEMS.map((item) => (
                <button
                  key={item.key}
                  className={`profile-sidebar__nav-item ${activeHubTab === item.key ? 'profile-sidebar__nav-item--active' : ''}`}
                  onClick={() => setTab(item.key)}
                >
                  {item.icon}
                  <span>{item.label}</span>
                  {item.key === 'notifications' && hasUnread && (
                    <span className="sidebar-unread-dot" />
                  )}
                </button>
              ))}
            </nav>

            {/* Logout */}
            <div className="profile-sidebar__footer">
              <button className="profile-sidebar__logout" onClick={logout}>
                <LogOut size={16} />
                Выйти
              </button>
            </div>
          </div>
        </aside>
      </div>

      {/* ═══ Modals ═══ */}
      {refundCoupon && (
        <RefundRequestModal coupon={refundCoupon} onClose={() => setRefundCoupon(null)} />
      )}
      {complaintCoupon && (
        <ComplaintModal coupon={complaintCoupon} onClose={() => setComplaintCoupon(null)} />
      )}
    </div>
  );
}
