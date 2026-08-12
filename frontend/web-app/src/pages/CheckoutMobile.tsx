import { useState } from 'react';
import { AlertCircle, ChevronLeft, Pencil, ShieldCheck } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { IMaskInput } from 'react-imask';
import { authApi } from '../api/auth';
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

/** Тот же формат, что и в LoginCard/ProfileSettingsSection: маска "+{998} 00 000-00-00" → +998XXXXXXXXX. */
const PHONE_PATTERN = /^\+998\d{9}$/;

/**
 * Мобильное оформление. Референс: «Мобилка - 5 Оплата», но честно по факту:
 * Telegram-доставки и Payme/Click на бэкенде нет — купон уходит в профиль,
 * оплата в демо-режиме подтверждается на следующем экране.
 * Телефон (верифицированный через OTP) обязателен, email — нет (T2/T6).
 */
export default function CheckoutMobile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { items, totalItems, totalPrice, clearCart } = useCartStore();
  const { isAuthenticated, user, refreshProfile } = useAuthStore();

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

  // Inline подтверждение телефона (T6), по образцу ProfileSettingsSection.
  const [phoneInput, setPhoneInput] = useState('');
  const [phoneCode, setPhoneCode] = useState('');
  const [phoneOtpStep, setPhoneOtpStep] = useState<'phone' | 'code'>('phone');
  const [phoneBusy, setPhoneBusy] = useState(false);
  const [phoneOtpError, setPhoneOtpError] = useState('');
  const [phoneNotice, setPhoneNotice] = useState('');

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
          <button
            type="button"
            className="cmpay__cta"
            onClick={() => navigate(lp('/login'), { state: { from: lp('/checkout') } })}
          >
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

  const requestPhoneLinkOtp = async () => {
    setPhoneOtpError('');
    const trimmed = phoneInput.trim();
    if (!PHONE_PATTERN.test(trimmed)) {
      setPhoneOtpError(t('checkout.addPhoneInvalid'));
      return;
    }

    const sessionGeneration = captureSessionGeneration();
    setPhoneBusy(true);
    setPhoneNotice('');
    try {
      await authApi.requestPhoneOtp(trimmed);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setPhoneNotice(t('checkout.addPhoneCodeSent'));
      setPhoneOtpStep('code');
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { data?: { message?: string } } };
      setPhoneOtpError(e.response?.data?.message || t('checkout.addPhoneRequestError'));
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setPhoneBusy(false);
      }
    }
  };

  /**
   * /auth/phone/link отвечает 200 без тела — обязательно перечитываем профиль
   * (refreshProfile), иначе checkout продолжит считать телефон непривязанным.
   */
  const submitPhoneLink = async () => {
    setPhoneOtpError('');
    const sessionGeneration = captureSessionGeneration();
    setPhoneBusy(true);
    try {
      await authApi.linkPhone({ phone: phoneInput.trim(), code: phoneCode.trim() });
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      await refreshProfile();
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      setPhoneCode('');
      setPhoneOtpStep('phone');
      setPhoneNotice(t('checkout.addPhoneSuccess'));
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const e = err as { response?: { status?: number; data?: { message?: string } } };
      const fallback =
        e.response?.status === 409
          ? t('checkout.addPhoneTaken')
          : e.response?.status === 401
            ? t('checkout.addPhoneInvalidCode')
            : t('checkout.addPhoneError');
      setPhoneOtpError(e.response?.data?.message || fallback);
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setPhoneBusy(false);
      }
    }
  };

  // Заказ создаётся по телефону из профиля — телефон должен быть привязан И подтверждён (T6).
  if (!user?.phoneVerified) {
    return (
      <div className="cmpay">
        {bar}
        <div className="cmpay__stop">
          <AlertCircle size={34} className="cmpay__stop-icon" />
          <p className="cmpay__stop-text">{t('checkout.addPhoneDesc')}</p>

          {phoneOtpStep === 'phone' ? (
            <label className="cmpay__stop-text" htmlFor="checkout-m-phone-input">
              {t('checkout.addPhoneLabel')}
              <IMaskInput
                id="checkout-m-phone-input"
                className="input-field"
                mask="+{998} 00 000-00-00"
                placeholder={t('checkout.addPhonePlaceholder')}
                value={phoneInput}
                onAccept={(val) => setPhoneInput(val.replace(/\s|-/g, ''))}
                autoFocus
              />
            </label>
          ) : (
            <label className="cmpay__stop-text" htmlFor="checkout-m-phone-code">
              {t('checkout.addPhoneCodeLabel')}
              <input
                id="checkout-m-phone-code"
                className="input-field"
                inputMode="numeric"
                maxLength={6}
                value={phoneCode}
                onChange={(e) => setPhoneCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                autoFocus
              />
            </label>
          )}

          {phoneNotice && <p className="cmpay__stop-text">{phoneNotice}</p>}
          {phoneOtpError && <p className="cmpay__error">{phoneOtpError}</p>}

          <button
            type="button"
            className="cmpay__cta"
            disabled={
              phoneBusy
              || (phoneOtpStep === 'phone'
                ? !PHONE_PATTERN.test(phoneInput.trim())
                : phoneCode.trim().length !== 6)
            }
            onClick={phoneOtpStep === 'phone' ? requestPhoneLinkOtp : submitPhoneLink}
          >
            {phoneBusy
              ? t('checkout.processing')
              : phoneOtpStep === 'phone'
                ? t('checkout.addPhoneSendCode')
                : t('checkout.addPhoneConfirm')}
          </button>

          {phoneOtpStep === 'code' && (
            <button
              type="button"
              className="cmpay__cta"
              disabled={phoneBusy}
              onClick={requestPhoneLinkOtp}
            >
              {t('checkout.addPhoneResend')}
            </button>
          )}
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
      // email опционален (T2/T6): синтетический placeholder-email на бэкенд не отправляем.
      const emailForOrder = user?.emailPlaceholder ? '' : email;
      const res = await ordersApi.createOrder(emailForOrder, phone);
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
          <span className="cmpay__card-sub">
            {[user?.emailPlaceholder ? '' : email, phone].filter(Boolean).join(' · ')}
          </span>
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
