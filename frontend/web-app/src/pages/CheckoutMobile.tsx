import { useState } from 'react';
import { AlertCircle, ChevronLeft, Pencil, ShieldCheck } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ordersApi } from '../api/orders';
import { useAuthStore } from '../store/authStore';
import { useCartStore } from '../store/cartStore';
import { useLocalePath } from '../hooks/useLocalePath';
import {
  captureSessionGeneration,
  isSessionGenerationCurrent,
} from '../sessionCleanup';
import { formatPrice } from '../utils/format';
import './CheckoutMobile.css';

/**
 * Мобильное оформление. Референс: «Мобилка - 5 Оплата», но честно по факту:
 * Telegram-доставки и Payme/Click на бэкенде нет — купон уходит в профиль,
 * оплата в демо-режиме подтверждается на следующем экране.
 */
export default function CheckoutMobile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { items, totalItems, totalPrice, clearCart } = useCartStore();
  const { isAuthenticated, user } = useAuthStore();

  const renderedSessionGeneration = captureSessionGeneration();
  const [loadingSessionGeneration, setLoadingSessionGeneration] =
    useState<number | null>(null);
  const [sessionError, setSessionError] = useState<{
    generation: number;
    message: string;
  } | null>(null);
  const loading = loadingSessionGeneration === renderedSessionGeneration;
  const error = sessionError?.generation === renderedSessionGeneration
    ? sessionError.message
    : '';

  const email = user?.email ?? '';
  const phone = user?.phone ?? '';

  const bar = (
    <div className="mbar cmpay__bar">
      <button
        type="button"
        className="mround"
        onClick={() => (window.history.length > 1 ? navigate(-1) : navigate(lp('/cart')))}
        aria-label={t('common.back')}
      >
        <ChevronLeft size={18} strokeWidth={2} />
      </button>
      <span className="mbar__title">{t('checkout.payTitle')}</span>
      <span className="cmpay__spacer" />
    </div>
  );

  if (!isAuthenticated) {
    return (
      <div className="cmpay">
        {bar}
        <div className="cmpay__stop">
          <h1 className="cmpay__stop-title">{t('checkout.authRequiredTitle')}</h1>
          <p className="cmpay__stop-text">{t('checkout.authRequiredDesc')}</p>
          <button type="button" className="cmpay__cta" onClick={() => navigate(lp('/login'))}>
            {t('checkout.login')}
          </button>
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="cmpay">
        {bar}
        <div className="cmpay__stop">
          <h1 className="cmpay__stop-title">{t('checkout.emptyTitle')}</h1>
          <button type="button" className="cmpay__cta" onClick={() => navigate(lp('/coupons'))}>
            {t('checkout.backToCatalog')}
          </button>
        </div>
      </div>
    );
  }

  // Заказ создаётся по email + телефону из профиля — без них backend откажет.
  if (!email || !phone) {
    return (
      <div className="cmpay">
        {bar}
        <div className="cmpay__stop">
          <AlertCircle size={34} className="cmpay__stop-icon" />
          <p className="cmpay__stop-text">
            {!email ? t('checkout.missingEmail') : t('checkout.missingPhone')}
          </p>
          <button
            type="button"
            className="cmpay__cta"
            onClick={() => navigate(`${lp('/profile')}?tab=profile`)}
          >
            {t('checkout.fillProfile')}
          </button>
        </div>
      </div>
    );
  }

  const submit = async () => {
    if (loading) return;
    const sessionGeneration = captureSessionGeneration();
    setLoadingSessionGeneration(sessionGeneration);
    setSessionError(null);
    try {
      const res = await ordersApi.createOrder(email, phone);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      clearCart();
      navigate(lp(`/payment/${res.data.data.id}`), { replace: true });
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { data?: { message?: string } } };
      setSessionError({
        generation: sessionGeneration,
        message: e.response?.data?.message || t('checkout.error'),
      });
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setLoadingSessionGeneration((current) =>
          current === sessionGeneration ? null : current);
      }
    }
  };

  return (
    <div className="cmpay">
      {bar}

      <h2 className="cmpay__label">{t('mobile.checkout.recipient')}</h2>

      <button
        type="button"
        className="cmpay__card cmpay__card--on"
        onClick={() => navigate(`${lp('/profile')}?tab=profile`)}
      >
        <span className="cmpay__dot cmpay__dot--on" />
        <span className="cmpay__card-text">
          <span className="cmpay__card-title">{t('mobile.checkout.toMe')}</span>
          <span className="cmpay__card-sub">{[email, phone].filter(Boolean).join(' · ')}</span>
        </span>
        <Pencil size={13} className="cmpay__card-edit" />
      </button>

      <p className="cmpay__hint">{t('mobile.checkout.deliveryNote')}</p>

      <h2 className="cmpay__label">{t('checkout.paymentTitle')}</h2>

      {/* Payme/Click в макете есть, но провайдеры не подключены (PAYMENT_MODE=demo) —
          показываем ровно то, что реально произойдёт. */}
      <div className="cmpay__method">
        <span className="cmpay__method-icon">
          <ShieldCheck size={15} />
        </span>
        <span className="cmpay__method-text">
          <span className="cmpay__card-title">{t('payment.demoBadge')}</span>
          <span className="cmpay__card-sub">{t('mobile.checkout.demoNote')}</span>
        </span>
        <span className="cmpay__dot cmpay__dot--on" />
      </div>

      {error && <p className="cmpay__error">{error}</p>}

      <div className="cmpay__total">
        <div className="cmpay__row">
          <span>{t('cart.itemsLine', { count: totalItems })}</span>
          <span className="cmpay__row-value">{formatPrice(totalPrice)}</span>
        </div>

        <div className="cmpay__grand">
          <span>{t('cart.summary')}</span>
          <span>{formatPrice(totalPrice)}</span>
        </div>

        <button type="button" className="cmpay__cta" onClick={submit} disabled={loading}>
          {loading ? t('checkout.processing') : t('checkout.payCta')}
        </button>
      </div>
    </div>
  );
}
