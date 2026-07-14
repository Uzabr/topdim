import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Ticket } from 'lucide-react';
import { useAuthStore } from '../store/authStore';
import { ordersApi } from '../api/orders';
import type { PurchasedCoupon } from '../api/orders';
import { complaintsApi } from '../api/complaints';
import { reviewsApi } from '../api/reviews';
import CouponTicket from '../components/profile/CouponTicket';
import ActiveCouponCard from '../components/profile/ActiveCouponCard';
import ReviewModal from '../components/profile/ReviewModal';
import RefundRequestModal from '../components/profile/RefundRequestModal';
import ComplaintModal from '../components/profile/ComplaintModal';
import ProfileSettingsSection from '../components/profile/ProfileSettingsSection';
import ProfileHelpSection from '../components/profile/ProfileHelpSection';
import DropTabs from '../components/ui/DropTabs';
import { useLocalePath } from '../hooks/useLocalePath';
import { formatDate, formatPrice } from '../utils/format';
import './ProfilePage.css';

export type ProfileTab = 'coupons' | 'orders' | 'settings' | 'help';

const TABS: ProfileTab[] = ['coupons', 'orders', 'settings', 'help'];

/** Купон ещё «на руках»: им можно воспользоваться или по нему идёт возврат. */
const LIVE_STATUSES: PurchasedCoupon['status'][] = ['ACTIVE', 'REFUND_PENDING'];

function getInitialTab(search: string): ProfileTab {
  const tab = new URLSearchParams(search).get('tab');
  if (TABS.includes(tab as ProfileTab)) return tab as ProfileTab;
  // Старые ссылки: ?tab=profile / ?tab=notifications / ?tab=refunds / ?tab=complaints
  if (tab) return 'settings';
  return 'coupons';
}

function archiveStatusClass(status: PurchasedCoupon['status']): string {
  if (status === 'USED') return 'used';
  if (status === 'EXPIRED') return 'expired';
  return 'refunded';
}

export default function ProfilePage() {
  const { t } = useTranslation();
  const { user, logout, isAuthenticated } = useAuthStore();
  const navigate = useNavigate();
  const location = useLocation();
  const lp = useLocalePath();

  const [tab, setActiveTab] = useState<ProfileTab>(() => getInitialTab(location.search));
  const [refundCoupon, setRefundCoupon] = useState<PurchasedCoupon | null>(null);
  const [complaintCoupon, setComplaintCoupon] = useState<PurchasedCoupon | null>(null);
  const [reviewCoupon, setReviewCoupon] = useState<PurchasedCoupon | null>(null);

  const setTab = (next: ProfileTab) => {
    setActiveTab(next);
    navigate(`${location.pathname}?tab=${next}`, { replace: true });
  };

  // Один запрос за всеми купонами вместо пяти по статусам — делим на списки на клиенте
  const { data: coupons = [], isLoading } = useQuery({
    queryKey: ['my-coupons', 'all'],
    queryFn: () => ordersApi.getMyCoupons(),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

  // Лениво: нужны только на табе купонов
  const { data: complaints = [] } = useQuery({
    queryKey: ['my-complaints'],
    queryFn: () => complaintsApi.getMine(0, 100),
    select: (res) => res.data.data.content,
    enabled: isAuthenticated && tab === 'coupons',
  });

  const { data: myReviews = [] } = useQuery({
    queryKey: ['my-reviews'],
    queryFn: () => reviewsApi.getMine(0, 100),
    select: (res) => res.data.data.content,
    enabled: isAuthenticated && tab === 'coupons',
  });

  // Лениво: только на табе покупок
  const { data: orders = [] } = useQuery({
    queryKey: ['my-orders'],
    queryFn: () => ordersApi.getOrders(0, 50),
    select: (res) => res.data.data.content,
    enabled: isAuthenticated && tab === 'orders',
  });

  const { live, archive, ticket, rest } = useMemo(() => {
    const liveList = coupons
      .filter((c) => LIVE_STATUSES.includes(c.status))
      .sort((a, b) => (a.expiresAt ?? '').localeCompare(b.expiresAt ?? ''));
    const archiveList = coupons.filter((c) => !LIVE_STATUSES.includes(c.status));
    return {
      live: liveList,
      archive: archiveList,
      ticket: liveList[0] ?? null,
      rest: liveList.slice(1),
    };
  }, [coupons]);

  const openComplaints = useMemo(
    () =>
      new Set(
        complaints
          .filter((c) => c.status === 'PENDING' || c.status === 'IN_REVIEW')
          .map((c) => c.purchasedCouponId),
      ),
    [complaints],
  );

  const reviewedOffers = useMemo(
    () => new Set(myReviews.map((r) => r.couponOfferId)),
    [myReviews],
  );

  if (!isAuthenticated) {
    return (
      <div className="profile-page container">
        <div className="profile-guest">
          <Ticket className="profile-empty-icon" size={48} strokeWidth={1.5} />
          <h2>{t('profile.greeting')}</h2>
          <p>{t('profile.guestDesc')}</p>
          <Link to={lp('/login')} className="primary-button">{t('profile.login')}</Link>
        </div>
      </div>
    );
  }

  return (
    <div className="profile-page container">
      {/* ═══ Заголовок ═══ */}
      <header className="profile-head">
        <div>
          <h1 className="profile-name">{user?.firstName}</h1>
          <p className="profile-email">{user?.email}</p>
        </div>
        <button type="button" className="profile-logout" onClick={logout}>
          {t('profile.logout')}
        </button>
      </header>

      {/* ═══ Табы: чёрная капля перетекает между кнопками + звук ═══ */}
      <DropTabs
        id="ptabs"
        active={tab}
        onChange={setTab}
        tabs={TABS.map((key) => ({
          key,
          label: t(`profile.tabs.${key}`),
          badge: key === 'coupons' ? live.length : undefined,
        }))}
      />

      {/* ═══ Мои купоны ═══ */}
      {tab === 'coupons' && (
        <section>
          {isLoading ? (
            <p className="profile-loading">{t('profile.loadingCoupons')}</p>
          ) : live.length === 0 && archive.length === 0 ? (
            <div className="profile-empty">
              <Ticket className="profile-empty-icon" size={44} strokeWidth={1.5} />
              <h3>{t('profile.emptyCoupons.active.title')}</h3>
              <p>{t('profile.emptyCoupons.active.text')}</p>
              <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>
                {t('profile.goCatalog')}
              </button>
            </div>
          ) : (
            <>
              {ticket && (
                <CouponTicket
                  coupon={ticket}
                  hasComplaint={openComplaints.has(ticket.id)}
                  onRefund={setRefundCoupon}
                  onComplain={setComplaintCoupon}
                />
              )}

              {rest.length > 0 && (
                <div className="profile-active-grid">
                  {rest.map((c) => (
                    <ActiveCouponCard
                      key={c.id}
                      coupon={c}
                      hasComplaint={openComplaints.has(c.id)}
                      onRefund={setRefundCoupon}
                      onComplain={setComplaintCoupon}
                    />
                  ))}
                </div>
              )}

              {archive.length > 0 && (
                <>
                  <div className="profile-archive-head">
                    <h2>{t('profile.archive.title')}</h2>
                    <span>{t('profile.archive.subtitle')}</span>
                  </div>

                  <div className="profile-archive">
                    {archive.map((c) => (
                      <div key={c.id} className="archive-row">
                        <span className={`archive-row__status archive-row__status--${archiveStatusClass(c.status)}`}>
                          {t(`profile.purchasedCoupon.status.${c.status.toLowerCase()}`)}
                        </span>
                        <span className="archive-row__title">{c.couponTitle}</span>
                        <span className="archive-row__date">
                          {formatDate(c.usedAt || c.expiresAt || c.purchasedAt)}
                        </span>
                        {c.status === 'USED' && !reviewedOffers.has(c.couponOfferId) && (
                          <button
                            type="button"
                            className="archive-row__review"
                            onClick={() => setReviewCoupon(c)}
                          >
                            {t('profile.archive.leaveReview')}
                          </button>
                        )}
                      </div>
                    ))}
                  </div>
                </>
              )}
            </>
          )}
        </section>
      )}

      {/* ═══ Покупки ═══ */}
      {tab === 'orders' && (
        <section className="profile-orders">
          {orders.length === 0 ? (
            <p className="profile-loading">{t('profile.orders.empty')}</p>
          ) : (
            <>
              {orders.map((o) => (
                <div key={o.id} className="order-row">
                  <span className="order-row__title">
                    {t('profile.orders.number', { number: o.orderNumber })}
                  </span>
                  <span className="order-row__date">{formatDate(o.createdAt)}</span>
                  <span className="order-row__sum">{formatPrice(o.totalAmount)}</span>
                  <span className={`order-row__status order-row__status--${o.status.toLowerCase()}`}>
                    {t(`profile.orders.status.${o.status.toLowerCase()}`, { defaultValue: o.status })}
                  </span>
                </div>
              ))}
              <p className="profile-orders__note">{t('profile.orders.note')}</p>
            </>
          )}
        </section>
      )}

      {tab === 'settings' && <ProfileSettingsSection />}
      {tab === 'help' && <ProfileHelpSection />}

      {/* ═══ Модалки ═══ */}
      {refundCoupon && (
        <RefundRequestModal coupon={refundCoupon} onClose={() => setRefundCoupon(null)} />
      )}
      {complaintCoupon && (
        <ComplaintModal coupon={complaintCoupon} onClose={() => setComplaintCoupon(null)} />
      )}
      {reviewCoupon && (
        <ReviewModal coupon={reviewCoupon} onClose={() => setReviewCoupon(null)} />
      )}
    </div>
  );
}
