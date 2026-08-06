import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { IMaskInput } from 'react-imask';
import { ShieldCheck, ChevronLeft, CreditCard, Smartphone, AlertCircle, Loader2, LogIn } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import { useAuthStore } from '../store/authStore';
import { authApi } from '../api/auth';
import { ordersApi } from '../api/orders';
import { useLocalePath } from '../hooks/useLocalePath';
import {
  captureSessionGeneration,
  isSessionGenerationCurrent,
} from '../sessionCleanup';
import './CheckoutPage.css';

/** Тот же формат, что и в LoginCard/ProfileSettingsSection: маска "+{998} 00 000-00-00" → +998XXXXXXXXX. */
const PHONE_PATTERN = /^\+998\d{9}$/;

/**
 * CheckoutPage — auth-only checkout.
 * Guest purchase path отключен.
 * Checkout требует авторизацию; телефон (верифицированный через OTP) обязателен,
 * email — нет (T2/T6: бэкенд принимает пустой email, реальный email — опционален).
 * Order создаётся из backend cart.
 */
export default function CheckoutDesktop() {
  const { t } = useTranslation();
  const { items, totalPrice, clearCart } = useCartStore();
  const { isAuthenticated, user, refreshProfile } = useAuthStore();
  const navigate = useNavigate();
  const lp = useLocalePath();

  const renderedSessionGeneration = captureSessionGeneration();
  const [loadingSessionGeneration, setLoadingSessionGeneration] =
    useState<number | null>(null);
  const [sessionError, setSessionError] = useState<{
    generation: number;
    message: string;
  } | null>(null);
  const isLoading = loadingSessionGeneration === renderedSessionGeneration;
  const error = sessionError?.generation === renderedSessionGeneration
    ? sessionError.message
    : '';
  const [paymentMethod, setPaymentMethod] = useState<'CARD' | 'CLICK' | 'PAYME'>('CARD');

  // Inline подтверждение телефона (T6), по образцу ProfileSettingsSection.
  const [phoneInput, setPhoneInput] = useState('');
  const [phoneCode, setPhoneCode] = useState('');
  const [phoneOtpStep, setPhoneOtpStep] = useState<'phone' | 'code'>('phone');
  const [phoneBusy, setPhoneBusy] = useState(false);
  const [phoneOtpError, setPhoneOtpError] = useState('');
  const [phoneNotice, setPhoneNotice] = useState('');

  // Auth guard: если не авторизован — показываем CTA для логина
  if (!isAuthenticated) {
    return (
      <div className="checkout-empty container">
        <LogIn size={48} className="checkout-empty__icon" />
        <h2>{t('checkout.authRequiredTitle')}</h2>
        <p>{t('checkout.authRequiredDesc')}</p>
        <button
          className="primary-button"
          onClick={() => navigate(lp('/login'), { state: { from: lp('/checkout') } })}
        >
          {t('checkout.login')}
        </button>
      </div>
    );
  }

  // Пустая корзина
  if (items.length === 0) {
    return (
      <div className="checkout-empty container">
        <AlertCircle size={48} className="checkout-empty__icon" />
        <h2>{t('checkout.emptyTitle')}</h2>
        <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>{t('checkout.backToCatalog')}</button>
      </div>
    );
  }

  // Email и phone из профиля пользователя. Email больше не обязателен (T6);
  // телефон обязателен и должен быть привязан (verified через OTP).
  const userEmail = user?.email || '';
  const userPhone = user?.phone || '';
  const needsPhone = !userPhone;

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

  // Blocking state: телефон не привязан — инлайновый шаг OTP-подтверждения.
  if (needsPhone) {
    return (
      <div className="checkout-page container">
        <div className="checkout-header">
          <button className="checkout-back" onClick={() => navigate(-1)} aria-label={t('common.back')}>
            <ChevronLeft size={24} />
          </button>
          <h1>{t('checkout.title')}</h1>
        </div>

        <div className="checkout-missing-contact surface-card">
          <AlertCircle size={40} className="checkout-missing-contact__icon" />
          <p className="checkout-missing-contact__text">{t('checkout.addPhoneDesc')}</p>

          <div className="checkout-form">
            {phoneOtpStep === 'phone' ? (
              <div className="form-group">
                <label htmlFor="checkout-phone-input">{t('checkout.addPhoneLabel')}</label>
                <IMaskInput
                  id="checkout-phone-input"
                  className="form-input"
                  mask="+{998} 00 000-00-00"
                  placeholder={t('checkout.addPhonePlaceholder')}
                  value={phoneInput}
                  onAccept={(val) => setPhoneInput(val.replace(/\s|-/g, ''))}
                  autoFocus
                />
              </div>
            ) : (
              <div className="form-group">
                <label htmlFor="checkout-phone-code">{t('checkout.addPhoneCodeLabel')}</label>
                <input
                  id="checkout-phone-code"
                  className="form-input form-input--center"
                  inputMode="numeric"
                  maxLength={6}
                  value={phoneCode}
                  onChange={(e) => setPhoneCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
                  autoFocus
                />
              </div>
            )}

            {phoneNotice && <p className="form-hint">{phoneNotice}</p>}
            {phoneOtpError && (
              <div className="checkout-error"><AlertCircle size={16} /> {phoneOtpError}</div>
            )}

            <button
              className="primary-button checkout-next-btn"
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
                className="checkout-edit-btn"
                disabled={phoneBusy}
                onClick={requestPhoneLinkOtp}
              >
                {t('checkout.addPhoneResend')}
              </button>
            )}
          </div>
        </div>
      </div>
    );
  }

  const handlePayment = async () => {
    const sessionGeneration = captureSessionGeneration();
    setLoadingSessionGeneration(sessionGeneration);
    setSessionError(null);
    try {
      // email опционален (T2/T6): синтетический placeholder-email на бэкенд не отправляем.
      const emailForOrder = user?.emailPlaceholder ? '' : (user?.email ?? '');
      const response = await ordersApi.createOrder(emailForOrder, userPhone);
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const order = response.data.data;
      clearCart();
      // Переход на страницу оплаты (Stage 6) или профиль
      navigate(lp(`/payment/${order.id}`), { replace: true });
    } catch (err: unknown) {
      if (!isSessionGenerationCurrent(sessionGeneration)) return;
      const error = err as { response?: { data?: { message?: string } } };
      setSessionError({
        generation: sessionGeneration,
        message: error.response?.data?.message || t('checkout.error'),
      });
    } finally {
      if (isSessionGenerationCurrent(sessionGeneration)) {
        setLoadingSessionGeneration((current) =>
          current === sessionGeneration ? null : current);
      }
    }
  };

  return (
    <div className="checkout-page container">
      <div className="checkout-header">
        <button className="checkout-back" onClick={() => navigate(-1)} aria-label={t('common.back')}>
          <ChevronLeft size={24} />
        </button>
        <h1>{t('checkout.title')}</h1>
      </div>

      <div className="checkout-layout">
        <div className="checkout-main">

          <div className="checkout-section surface-card">
            <h2 className="checkout-section__title">{t('checkout.contactTitle')}</h2>
            <div className="checkout-contact-info">
              <div className="checkout-contact-row">
                <span className="checkout-contact-label">Email:</span>
                <span className="checkout-contact-value">
                  {user?.emailPlaceholder ? '—' : (userEmail || '—')}
                </span>
              </div>
              <div className="checkout-contact-row">
                <span className="checkout-contact-label">{t('checkout.phoneLabel')}</span>
                <span className="checkout-contact-value">{userPhone || '—'}</span>
              </div>
            </div>
          </div>

          {/* Payment Method */}
          <div className="checkout-section surface-card">
            <h2 className="checkout-section__title">{t('checkout.paymentTitle')}</h2>
            
            {error && <div className="checkout-error"><AlertCircle size={16} /> {error}</div>}

            <div className="payment-methods">
              <label className={`payment-method ${paymentMethod === 'CARD' ? 'payment-method--active' : ''}`}>
                <input 
                  type="radio" 
                  name="payment" 
                  checked={paymentMethod === 'CARD'} 
                  onChange={() => setPaymentMethod('CARD')}
                />
                <CreditCard size={24} />
                <span>{t('checkout.card')}</span>
              </label>
              
              <label className={`payment-method ${paymentMethod === 'CLICK' ? 'payment-method--active' : ''}`}>
                <input 
                  type="radio" 
                  name="payment" 
                  checked={paymentMethod === 'CLICK'} 
                  onChange={() => setPaymentMethod('CLICK')}
                />
                <Smartphone size={24} />
                <span>Click</span>
              </label>

              <label className={`payment-method ${paymentMethod === 'PAYME' ? 'payment-method--active' : ''}`}>
                <input 
                  type="radio" 
                  name="payment" 
                  checked={paymentMethod === 'PAYME'} 
                  onChange={() => setPaymentMethod('PAYME')}
                />
                <Smartphone size={24} />
                <span>Payme</span>
              </label>
            </div>

            <button
              className="primary-button checkout-pay-btn"
              onClick={handlePayment}
              disabled={isLoading}
            >
              {isLoading ? <><Loader2 size={18} className="spin" /> {t('checkout.processing')}</> : t('checkout.pay', { amount: formatPrice(totalPrice) })}
            </button>
            <p className="secure-badge"><ShieldCheck size={14} /> {t('checkout.secure')}</p>
          </div>
        </div>

        {/* Sidebar Summary */}
        <div className="checkout-sidebar">
          <div className="checkout-summary surface-card">
            <h3>{t('checkout.yourOrder')}</h3>
            <div className="checkout-items">
              {items.map((item) => (
               <div key={item.key} className="checkout-item">
                 <div className="checkout-item__info">
                   <span className="checkout-item__title">{item.couponTitle}</span>
                   <span className="checkout-item__option">{item.optionTitle}</span>
                 </div>
                 <div className="checkout-item__price-block">
                   <span className="checkout-item__price">{formatPrice(item.unitPrice * item.quantity)}</span>
                   <span className="checkout-item__qty">{item.quantity} {t('common.units.pcs')}</span>
                 </div>
               </div> 
              ))}
            </div>
            
            <div className="checkout-totals">
              <div className="checkout-total-row">
                <span>{t('checkout.total')}</span>
                <span className="checkout-final-price">{formatPrice(totalPrice)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
