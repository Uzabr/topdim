import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { ShieldCheck, ChevronLeft, CreditCard, Smartphone, AlertCircle, Loader2, LogIn } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import { useAuthStore } from '../store/authStore';
import { ordersApi } from '../api/orders';
import { useLocalePath } from '../hooks/useLocalePath';
import {
  captureSessionGeneration,
  isSessionGenerationCurrent,
} from '../sessionCleanup';
import './CheckoutPage.css';

/**
 * CheckoutPage — auth-only checkout.
 * Guest purchase path отключен.
 * Checkout требует авторизацию, email+phone берутся из user profile.
 * Order создаётся из backend cart.
 */
export default function CheckoutDesktop() {
  const { t } = useTranslation();
  const { items, totalPrice, clearCart } = useCartStore();
  const { isAuthenticated, user } = useAuthStore();
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

  // Auth guard: если не авторизован — показываем CTA для логина
  if (!isAuthenticated) {
    return (
      <div className="checkout-empty container">
        <LogIn size={48} className="checkout-empty__icon" />
        <h2>{t('checkout.authRequiredTitle')}</h2>
        <p>{t('checkout.authRequiredDesc')}</p>
        <button className="primary-button" onClick={() => navigate(lp('/login'))}>
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

  // Email и phone из профиля пользователя
  const userEmail = user?.email || '';
  const userPhone = user?.phone || '';
  const missingContact = !userEmail || !userPhone;

  // Blocking state: missing contact data
  if (missingContact) {
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
          {!userEmail && (
            <p className="checkout-missing-contact__text">
              {t('checkout.missingEmail')}
            </p>
          )}
          {!userPhone && (
            <p className="checkout-missing-contact__text">
              {t('checkout.missingPhone')}
            </p>
          )}
          <button
            className="primary-button"
            onClick={() => navigate(lp('/profile') + '?tab=profile')}
            id="checkout-fill-profile-btn"
          >
            {t('checkout.fillProfile')}
          </button>
        </div>
      </div>
    );
  }

  const handlePayment = async () => {
    const sessionGeneration = captureSessionGeneration();
    setLoadingSessionGeneration(sessionGeneration);
    setSessionError(null);
    try {
      // Создаём order из backend cart с валидным email + phone
      const response = await ordersApi.createOrder(userEmail, userPhone);
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
                <span className="checkout-contact-value">{userEmail || '—'}</span>
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
