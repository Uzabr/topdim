import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
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

export default function ProfilePage() {
  const { t } = useTranslation();
  const { user, logout, isAuthenticated } = useAuthStore();

  const sidebarItems = useMemo(() => [
    { key: 'coupons' as ProfileTab, label: t('profile.tabs.coupons'), icon: <Ticket size={18} /> },
    { key: 'orders' as ProfileTab, label: t('profile.tabs.orders'), icon: <ShoppingBag size={18} /> },
    { key: 'refunds' as ProfileTab, label: t('profile.tabs.refunds'), icon: <RotateCcw size={18} /> },
    { key: 'complaints' as ProfileTab, label: t('profile.tabs.complaints'), icon: <MessageSquare size={18} /> },
    { key: 'notifications' as ProfileTab, label: t('profile.tabs.notifications'), icon: <Bell size={18} /> },
    { key: 'profile' as ProfileTab, label: t('profile.tabs.settings'), icon: <Settings size={18} /> },
    { key: 'help' as ProfileTab, label: t('profile.tabs.help'), icon: <HelpCircle size={18} /> },
  ], [t]);

  const couponTabs = useMemo(() => [
    { key: 'ACTIVE', label: t('profile.couponSubtabs.active'), icon: <Clock size={16} /> },
    { key: 'REFUND_PENDING', label: t('profile.couponSubtabs.refundPending'), icon: <RotateCcw size={16} /> },
    { key: 'USED', label: t('profile.couponSubtabs.used'), icon: <CheckCircle size={16} /> },
    { key: 'EXPIRED', label: t('profile.couponSubtabs.expired'), icon: <AlertCircle size={16} /> },
    { key: 'REFUNDED', label: t('profile.couponSubtabs.refunded'), icon: <RotateCcw size={16} /> },
  ], [t]);

  const emptyCouponCopy = useMemo(() => ({
    ACTIVE: { title: t('profile.emptyCoupons.active.title'), text: t('profile.emptyCoupons.active.text') },
    REFUND_PENDING: { title: t('profile.emptyCoupons.refundPending.title'), text: t('profile.emptyCoupons.refundPending.text') },
    USED: { title: t('profile.emptyCoupons.used.title'), text: t('profile.emptyCoupons.used.text') },
    EXPIRED: { title: t('profile.emptyCoupons.expired.title'), text: t('profile.emptyCoupons.expired.text') },
    REFUNDED: { title: t('profile.emptyCoupons.refunded.title'), text: t('profile.emptyCoupons.refunded.text') },
  }), [t]);
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
          <h2>{t('profile.greeting')}</h2>
          <p>{t('profile.guestDesc')}</p>
          <Link to={lp('/login')} className="primary-button">{t('profile.login')}</Link>
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
                <h2 className="profile-section-title">{t('profile.tabs.coupons')}</h2>
                <Tabs
                  tabs={couponTabs}
                  activeKey={couponSubTab}
                  onChange={setCouponSubTab}
                />
                <div className="profile-coupons">
                  {couponsLoading ? (
                    <div className="profile-loading">{t('profile.loadingCoupons')}</div>
                  ) : coupons.length === 0 ? (
                    <div className="profile-coupons-empty glass-card">
                      <span className="profile-empty-icon">📋</span>
                      <h3>{emptyCouponCopy[couponSubTab as keyof typeof emptyCouponCopy]?.title || t('profile.emptyCoupons.none')}</h3>
                      <p>{emptyCouponCopy[couponSubTab as keyof typeof emptyCouponCopy]?.text || t('profile.emptyCoupons.defaultText')}</p>
                      {couponSubTab === 'ACTIVE' && (
                        <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
                          {t('profile.goCatalog')}
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
                <h2 className="profile-section-title">{t('profile.tabs.orders')}</h2>
                <OrderHistorySection onTabChange={setTab} />
              </>
            )}

            {activeHubTab === 'refunds' && (
              <>
                <h2 className="profile-section-title">{t('profile.tabs.refunds')}</h2>
                <RefundsSection />
              </>
            )}

            {activeHubTab === 'complaints' && (
              <>
                <h2 className="profile-section-title">{t('profile.tabs.complaints')}</h2>
                <ComplaintsSection />
              </>
            )}

            {activeHubTab === 'notifications' && (
              <>
                <h2 className="profile-section-title">{t('profile.tabs.notifications')}</h2>
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
              {sidebarItems.map((item) => (
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
                {t('profile.logout')}
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
