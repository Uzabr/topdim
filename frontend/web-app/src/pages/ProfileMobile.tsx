import { useMemo, useState } from 'react';
import { ChevronLeft, ChevronRight, Ticket } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { complaintsApi } from '../api/complaints';
import { ordersApi } from '../api/orders';
import type { PurchasedCoupon } from '../api/orders';
import { reviewsApi } from '../api/reviews';
import ComplaintModal from '../components/profile/ComplaintModal';
import { getCouponActions } from '../components/profile/couponActions';
import ProfileHelpSection from '../components/profile/ProfileHelpSection';
import ProfileSettingsSection from '../components/profile/ProfileSettingsSection';
import RefundRequestModal from '../components/profile/RefundRequestModal';
import ReviewModal from '../components/profile/ReviewModal';
import UserAvatar from '../components/ui/UserAvatar';
import { SUPPORT_TELEGRAM_URL } from '../config/features';
import { useLocalePath } from '../hooks/useLocalePath';
import { useAuthStore } from '../store/authStore';
import { buildQrPayload } from '../utils/coupon';
import { daysUntil, formatDate, formatPrice } from '../utils/format';
import './ProfileMobile.css';

type Tab = 'coupons' | 'orders' | 'settings' | 'help';

const TABS: Tab[] = ['coupons', 'orders', 'settings', 'help'];

/** Купон ещё «на руках»: им можно воспользоваться или по нему идёт возврат. */
const LIVE: PurchasedCoupon['status'][] = ['ACTIVE', 'REFUND_PENDING'];

function tabOf(search: string): Tab {
  const tab = new URLSearchParams(search).get('tab');
  return TABS.includes(tab as Tab) ? (tab as Tab) : 'coupons';
}

function archiveClass(status: PurchasedCoupon['status']): string {
  if (status === 'USED') return 'used';
  if (status === 'EXPIRED') return 'expired';
  return 'refunded';
}

/** Профиль на мобиле. Референс: «Мобилка - 7 Профиль». */
export default function ProfileMobile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const location = useLocation();
  const lp = useLocalePath();
  const { user, logout, isAuthenticated } = useAuthStore();

  const tab = tabOf(location.search);
  const setTab = (next: Tab) =>
    navigate(next === 'coupons' ? location.pathname : `${location.pathname}?tab=${next}`);

  const [refund, setRefund] = useState<PurchasedCoupon | null>(null);
  const [complaint, setComplaint] = useState<PurchasedCoupon | null>(null);
  const [review, setReview] = useState<PurchasedCoupon | null>(null);

  const { data: coupons = [], isLoading } = useQuery({
    queryKey: ['my-coupons', 'all'],
    queryFn: () => ordersApi.getMyCoupons(),
    select: (res) => res.data.data,
    enabled: isAuthenticated,
  });

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

  const { data: orders = [] } = useQuery({
    queryKey: ['my-orders'],
    queryFn: () => ordersApi.getOrders(0, 50),
    select: (res) => res.data.data.content,
    enabled: isAuthenticated && tab === 'orders',
  });

  const { ticket, rest, archive } = useMemo(() => {
    const live = coupons
      .filter((c) => LIVE.includes(c.status))
      .sort((a, b) => (a.expiresAt ?? '').localeCompare(b.expiresAt ?? ''));
    return {
      ticket: live[0] ?? null,
      rest: live.slice(1),
      archive: coupons.filter((c) => !LIVE.includes(c.status)),
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

  const reviewed = useMemo(() => new Set(myReviews.map((r) => r.couponOfferId)), [myReviews]);

  if (!isAuthenticated) {
    return (
      <div className="pmob">
        <div className="pmob__guest">
          <Ticket size={44} strokeWidth={1.5} className="pmob__guest-icon" />
          <h1 className="pmob__guest-title">{t('profile.greeting')}</h1>
          <p className="pmob__guest-text">{t('profile.guestDesc')}</p>
          <Link to={lp('/login')} className="pmob__guest-btn">
            {t('profile.login')}
          </Link>
        </div>
      </div>
    );
  }

  const days = ticket?.expiresAt ? daysUntil(ticket.expiresAt) : null;
  const ticketActions = ticket
    ? getCouponActions(
        ticket.status,
        openComplaints.has(ticket.id),
        reviewed.has(ticket.couponOfferId),
      )
    : null;

  return (
    <div className="pmob">
      <div className="mbar pmob__bar">
        <button
          type="button"
          className="mround mround--glass"
          onClick={() =>
            tab === 'coupons'
              ? window.history.length > 1
                ? navigate(-1)
                : navigate(lp('/'))
              : setTab('coupons')
          }
          aria-label={t('common.back')}
        >
          <ChevronLeft size={18} strokeWidth={2} />
        </button>

        <span className="mbar__title">
          {tab === 'coupons' ? t('bottomNav.profile') : t(`profile.tabs.${tab}`)}
        </span>

        <UserAvatar
          avatarUrl={user?.avatarUrl}
          firstName={user?.firstName}
          className="pmob__avatar"
        />
      </div>

      {tab === 'coupons' && (
        <>
          <div className="pmob__head">
            <h1 className="pmob__name">{user?.firstName}</h1>
            <p className="pmob__contact">{user?.phone || user?.email}</p>
          </div>

          {isLoading ? (
            <p className="pmob__loading">{t('profile.loadingCoupons')}</p>
          ) : coupons.length === 0 ? (
            <div className="pmob__empty">
              <Ticket size={40} strokeWidth={1.5} className="pmob__guest-icon" />
              <h2 className="pmob__guest-title">{t('profile.emptyCoupons.active.title')}</h2>
              <p className="pmob__guest-text">{t('profile.emptyCoupons.active.text')}</p>
              <button
                type="button"
                className="pmob__guest-btn"
                onClick={() => navigate(lp('/coupons'))}
              >
                {t('profile.goCatalog')}
              </button>
            </div>
          ) : (
            <>
              {ticket && (
                <article className="pticket">
                  <div className="pticket__top">
                    <div className="pticket__row">
                      <span className="pticket__label">{t('profile.ticket.yours')}</span>
                      {days !== null && (
                        <span className="pticket__expiry">
                          {days <= 0
                            ? t('profile.ticket.expiresToday')
                            : t('profile.ticket.expiresInDays', { count: days })}
                        </span>
                      )}
                    </div>

                    <h2 className="pticket__title">{ticket.couponTitle}</h2>
                    <p className="pticket__meta">
                      {[ticket.merchantName, ticket.merchantAddress].filter(Boolean).join(' · ')}
                    </p>
                  </div>

                  <div className="pticket__perf">
                    <span className="pticket__notch pticket__notch--l" />
                    <span className="pticket__notch pticket__notch--r" />
                  </div>

                  <div className="pticket__bottom">
                    {ticket.qrToken && (
                      <div className="pticket__qr">
                        <QRCodeSVG value={buildQrPayload(ticket.qrToken)} size={88} level="M" />
                      </div>
                    )}

                    <div className="pticket__side">
                      <p className="pticket__hint">{t('profile.purchasedCoupon.qrHint')}</p>
                      <p className="pticket__code">
                        {t('profile.ticket.code')} <span>{ticket.couponCode}</span>
                      </p>

                      <div className="pticket__acts">
                        {ticketActions?.canRefund && (
                          <button
                            type="button"
                            className="pticket__act"
                            onClick={() => setRefund(ticket)}
                          >
                            {t('profile.refundMoney')}
                          </button>
                        )}
                        {ticketActions?.canComplain && (
                          <button
                            type="button"
                            className="pticket__act"
                            onClick={() => setComplaint(ticket)}
                          >
                            {t('profile.complain')}
                          </button>
                        )}
                        {openComplaints.has(ticket.id) && (
                          <span className="pticket__pending">{t('profile.complaintPending')}</span>
                        )}
                      </div>
                    </div>
                  </div>
                </article>
              )}

              {rest.length > 0 && (
                <>
                  <h2 className="pmob__section">{t('profile.couponSubtabs.active')}</h2>
                  <div className="pmob__list">
                    {rest.map((c) => {
                      const actions = getCouponActions(
                        c.status,
                        openComplaints.has(c.id),
                        reviewed.has(c.couponOfferId),
                      );

                      return (
                        <div key={c.id} className="prow">
                          <div className="prow__qr">
                            {c.qrToken && (
                              <QRCodeSVG value={buildQrPayload(c.qrToken)} size={46} level="M" />
                            )}
                          </div>

                          <div className="prow__text">
                            <span className="prow__title">{c.couponTitle}</span>
                            <span className="prow__meta">
                              {c.expiresAt &&
                                `${t('profile.ticket.until', { date: formatDate(c.expiresAt) })} · `}
                              <span className="prow__code">{c.couponCode}</span>
                            </span>
                            <span className="pticket__acts">
                              {actions.canRefund && (
                                <button
                                  type="button"
                                  className="pticket__act"
                                  onClick={() => setRefund(c)}
                                >
                                  {t('profile.refundShort')}
                                </button>
                              )}
                              {actions.canComplain && (
                                <button
                                  type="button"
                                  className="pticket__act"
                                  onClick={() => setComplaint(c)}
                                >
                                  {t('profile.complain')}
                                </button>
                              )}
                              {openComplaints.has(c.id) && (
                                <span className="pticket__pending">
                                  {t('profile.complaintPending')}
                                </span>
                              )}
                            </span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </>
              )}

              {archive.length > 0 && (
                <>
                  <h2 className="pmob__section">{t('profile.archive.title')}</h2>
                  <div className="pmob__list">
                    {archive.map((c) => {
                      const actions = getCouponActions(
                        c.status,
                        openComplaints.has(c.id),
                        reviewed.has(c.couponOfferId),
                      );

                      return (
                        <div key={c.id} className="parc">
                          <div className="parc__row">
                            <div className="parc__text">
                              <span className="parc__title">{c.couponTitle}</span>
                              <span className="parc__date">
                                {formatDate(c.usedAt || c.expiresAt || c.purchasedAt)}
                              </span>
                            </div>

                            <span className={`parc__badge parc__badge--${archiveClass(c.status)}`}>
                              {t(`profile.purchasedCoupon.status.${c.status.toLowerCase()}`)}
                            </span>
                          </div>

                          {actions.canComplain && (
                            <button
                              type="button"
                              className="parc__review"
                              onClick={() => setComplaint(c)}
                            >
                              {t('profile.complain')}
                            </button>
                          )}
                          {openComplaints.has(c.id) && (
                            <span className="pticket__pending">
                              {t('profile.complaintPending')}
                            </span>
                          )}
                          {/* Отзыв можно оставить только по использованному купону —
                              так же требует и бэкенд (reviews eligibility). */}
                          {actions.canReview && (
                            <button
                              type="button"
                              className="parc__review"
                              onClick={() => setReview(c)}
                            >
                              {t('profile.archive.leaveReview')}
                            </button>
                          )}
                        </div>
                      );
                    })}
                  </div>
                </>
              )}
            </>
          )}

          <div className="pmob__more">
            <button type="button" className="pmob__link" onClick={() => setTab('orders')}>
              {t('profile.tabs.orders')}
              <ChevronRight size={15} />
            </button>
            <button type="button" className="pmob__link" onClick={() => setTab('settings')}>
              {t('profile.tabs.settings')}
              <ChevronRight size={15} />
            </button>
            <button type="button" className="pmob__link pmob__link--muted" onClick={logout}>
              {t('profile.logout')}
            </button>
          </div>

          <section className="pmob__help">
            <h2 className="pmob__help-title">{t('profile.help.title')}</h2>
            <p className="pmob__help-text">{t('profile.help.contactHint')}</p>
            <a
              className="pmob__help-btn"
              href={SUPPORT_TELEGRAM_URL}
              target="_blank"
              rel="noopener noreferrer"
            >
              {t('profile.help.contact')}
            </a>
            <button type="button" className="pmob__help-more" onClick={() => setTab('help')}>
              {t('profile.tabs.help')}
            </button>
          </section>
        </>
      )}

      {tab === 'orders' && (
        <div className="pmob__list pmob__list--top">
          {orders.length === 0 ? (
            <p className="pmob__loading">{t('profile.orders.empty')}</p>
          ) : (
            orders.map((o) => (
              <div key={o.id} className="porder">
                <div className="porder__text">
                  <span className="prow__title">
                    {/* Название оффера, когда backend его отдаёт (title); иначе «Заказ №N».
                        См. TODO(backend) в api/orders.ts. */}
                    {o.title || t('profile.orders.number', { number: o.orderNumber })}
                  </span>
                  <span className="prow__meta">{formatDate(o.createdAt)}</span>
                </div>
                <div className="porder__right">
                  <span className="porder__sum">{formatPrice(o.totalAmount)}</span>
                  <span className="porder__status">
                    {t(`profile.orders.status.${o.status.toLowerCase()}`, {
                      defaultValue: o.status,
                    })}
                  </span>
                </div>
              </div>
            ))
          )}
        </div>
      )}

      {tab === 'settings' && (
        <div className="pmob__pane">
          <ProfileSettingsSection />
        </div>
      )}

      {tab === 'help' && (
        <div className="pmob__pane">
          <ProfileHelpSection />
        </div>
      )}

      {refund && <RefundRequestModal coupon={refund} onClose={() => setRefund(null)} />}
      {complaint && <ComplaintModal coupon={complaint} onClose={() => setComplaint(null)} />}
      {review && <ReviewModal coupon={review} onClose={() => setReview(null)} />}
    </div>
  );
}
