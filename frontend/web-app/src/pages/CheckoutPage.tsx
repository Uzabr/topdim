import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ShieldCheck, ChevronLeft, CreditCard, Smartphone, AlertCircle, Loader2, LogIn } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import { useAuthStore } from '../store/authStore';
import { ordersApi } from '../api/orders';
import { useLocalePath } from '../hooks/useLocalePath';
import './CheckoutPage.css';

/**
 * CheckoutPage — auth-only checkout.
 * Guest purchase path отключен.
 * Checkout требует авторизацию, email+phone берутся из user profile.
 * Order создаётся из backend cart.
 */
export default function CheckoutPage() {
  const { items, totalPrice, clearCart } = useCartStore();
  const { isAuthenticated, user } = useAuthStore();
  const navigate = useNavigate();
  const lp = useLocalePath();

  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<'CARD' | 'CLICK' | 'PAYME'>('CARD');

  // Auth guard: если не авторизован — показываем CTA для логина
  if (!isAuthenticated) {
    return (
      <div className="checkout-empty container">
        <LogIn size={48} className="checkout-empty__icon" />
        <h2>Необходимо войти в аккаунт</h2>
        <p>Для оформления заказа необходимо авторизоваться.</p>
        <button className="primary-button" onClick={() => navigate(lp('/login'))}>
          Войти
        </button>
      </div>
    );
  }

  // Пустая корзина
  if (items.length === 0) {
    return (
      <div className="checkout-empty container">
        <AlertCircle size={48} className="checkout-empty__icon" />
        <h2>Нет товаров для оформления</h2>
        <button className="primary-button" onClick={() => navigate(lp('/coupons'))}>Вернуться в каталог</button>
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
          <button className="checkout-back" onClick={() => navigate(-1)} aria-label="Назад">
            <ChevronLeft size={24} />
          </button>
          <h1>Оформление заказа</h1>
        </div>

        <div className="checkout-missing-contact glass-card">
          <AlertCircle size={40} className="checkout-missing-contact__icon" />
          {!userEmail && (
            <p className="checkout-missing-contact__text">
              В профиле не указан email. Добавьте email в аккаунт или обратитесь в поддержку.
            </p>
          )}
          {!userPhone && (
            <p className="checkout-missing-contact__text">
              В профиле не указан телефон. Добавьте телефон, чтобы мы могли связать заказ и купон с вашим аккаунтом.
            </p>
          )}
          <button
            className="primary-button"
            onClick={() => navigate(lp('/profile') + '?tab=profile')}
            id="checkout-fill-profile-btn"
          >
            Заполнить профиль
          </button>
        </div>
      </div>
    );
  }

  const handlePayment = async () => {

    setIsLoading(true);
    setError('');
    try {
      // Создаём order из backend cart с валидным email + phone
      const response = await ordersApi.createOrder(userEmail, userPhone);
      const order = response.data.data;
      clearCart();
      // Переход на страницу оплаты (Stage 6) или профиль
      navigate(lp(`/payment/${order.id}`), { replace: true });
    } catch (err: unknown) {
      const error = err as { response?: { data?: { message?: string } } };
      setError(error.response?.data?.message || 'Ошибка оформления заказа');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="checkout-page container">
      <div className="checkout-header">
        <button className="checkout-back" onClick={() => navigate(-1)} aria-label="Назад">
          <ChevronLeft size={24} />
        </button>
        <h1>Оформление заказа</h1>
      </div>

      <div className="checkout-layout">
        {/* Main Content */}
        <div className="checkout-main">

          {/* Contact Info (read-only from profile) */}
          <div className="checkout-section glass-card">
            <h2 className="checkout-section__title">Контактные данные</h2>
            <div className="checkout-contact-info">
              <div className="checkout-contact-row">
                <span className="checkout-contact-label">Email:</span>
                <span className="checkout-contact-value">{userEmail || '—'}</span>
              </div>
              <div className="checkout-contact-row">
                <span className="checkout-contact-label">Телефон:</span>
                <span className="checkout-contact-value">{userPhone || '—'}</span>
              </div>
            </div>
          </div>

          {/* Payment Method */}
          <div className="checkout-section glass-card">
            <h2 className="checkout-section__title">Оплата</h2>
            
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
                <span>Банковская карта</span>
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
              {isLoading ? <><Loader2 size={18} className="spin" /> Оформляем...</> : `Оплатить ${formatPrice(totalPrice)}`}
            </button>
            <p className="secure-badge"><ShieldCheck size={14} /> Платеж защищен шифрованием</p>
          </div>
        </div>

        {/* Sidebar Summary */}
        <div className="checkout-sidebar">
          <div className="checkout-summary glass-card">
            <h3>Ваш заказ</h3>
            <div className="checkout-items">
              {items.map((item) => (
               <div key={item.key} className="checkout-item">
                 <div className="checkout-item__info">
                   <span className="checkout-item__title">{item.couponTitle}</span>
                   <span className="checkout-item__option">{item.optionTitle}</span>
                 </div>
                 <div className="checkout-item__price-block">
                   <span className="checkout-item__price">{formatPrice(item.unitPrice * item.quantity)}</span>
                   <span className="checkout-item__qty">{item.quantity} шт.</span>
                 </div>
               </div> 
              ))}
            </div>
            
            <div className="checkout-totals">
              <div className="checkout-total-row">
                <span>Итого:</span>
                <span className="checkout-final-price">{formatPrice(totalPrice)}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
