import { useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { ShieldCheck, ChevronLeft, CreditCard, Smartphone, AlertCircle, Loader2 } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import { useAuthStore } from '../store/authStore';
import { authApi } from '../api/auth';
import { ordersApi } from '../api/orders';
import './CheckoutPage.css';

export default function CheckoutPage() {
  const [searchParams] = useSearchParams();
  const isGuest = searchParams.get('guest') === 'true';
  const couponId = searchParams.get('couponId');
  const optionId = searchParams.get('optionId');
  
  const { items, totalPrice, clearCart } = useCartStore();
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  const [step, setStep] = useState(isGuest && !isAuthenticated ? 1 : 2);
  const [phone, setPhone] = useState('');
  const [name, setName] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');
  const [paymentMethod, setPaymentMethod] = useState<'CARD' | 'CLICK' | 'PAYME'>('CARD');

  const displayItems = items.length > 0 ? items : (
    couponId && optionId ? [{ id: 'mock', couponTitle: 'Скидка на пиццу', optionTitle: 'Пицца 33см + напиток', unitPrice: 45000, quantity: 1 }] : []
  );
  
  const displayTotal = items.length > 0 ? totalPrice : 45000;

  if (displayItems.length === 0) {
    return (
      <div className="checkout-empty container">
        <AlertCircle size={48} className="checkout-empty__icon" />
        <h2>Нет товаров для оформления</h2>
        <button className="primary-button" onClick={() => navigate('/coupons')}>Вернуться в каталог</button>
      </div>
    );
  }

  const handleGuestAuth = async (e: React.FormEvent) => {
    e.preventDefault();
    if (phone.length < 9 || !name) return;
    
    setIsLoading(true);
    setError('');
    try {
      const fullPhone = '+998' + phone;
      const response = await authApi.guestAuth({ phone: fullPhone, name });
      const { accessToken, refreshToken, user } = response.data.data;
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('refreshToken', refreshToken);
      localStorage.setItem('user', JSON.stringify(user));
      setStep(2);
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка авторизации');
    } finally {
      setIsLoading(false);
    }
  };

  const handlePayment = async () => {
    setIsLoading(true);
    setError('');
    try {
      const fullPhone = phone ? '+998' + phone : '';
      await ordersApi.createOrder('', fullPhone);
      clearCart();
      navigate('/profile');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Ошибка оформления заказа');
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
          
          {/* STEP 1: Contacts (Guest only) */}
          {step === 1 && (
            <div className="checkout-section glass-card">
              <h2 className="checkout-section__title">1. Контактные данные</h2>
              <p className="checkout-section__subtitle">Куда прислать купленные купоны?</p>
              
              {error && <div className="checkout-error"><AlertCircle size={16} /> {error}</div>}

              <form className="checkout-form" onSubmit={handleGuestAuth}>
                <div className="form-group">
                  <label>Имя</label>
                  <input 
                    type="text" 
                    placeholder="Иван Иванов" 
                    className="form-input"
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    required 
                    disabled={isLoading}
                  />
                </div>
                <div className="form-group">
                  <label>Телефон</label>
                  <div className="form-input-wrapper">
                    <span className="form-input-prefix">+998</span>
                    <input 
                      type="tel" 
                      placeholder="90 123 45 67" 
                      className="form-input" 
                      value={phone}
                      onChange={(e) => setPhone(e.target.value.replace(/\D/g, '').slice(0, 9))}
                      required
                      disabled={isLoading}
                    />
                  </div>
                </div>
                <button type="submit" className="primary-button checkout-next-btn" disabled={isLoading || phone.length < 9 || !name}>
                  {isLoading ? <><Loader2 size={18} className="spin" /> Проверяем...</> : 'Продолжить к оплате'}
                </button>
              </form>
            </div>
          )}

          {/* STEP 2: Payment */}
          {step === 2 && (
            <div className="checkout-section glass-card">
              <h2 className="checkout-section__title">
                {step === 2 && isGuest ? '2. Оплата' : 'Оплата'}
              </h2>
              
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

              {paymentMethod === 'CARD' && (
                <form className="card-form" onSubmit={(e) => { e.preventDefault(); handlePayment(); }}>
                  <div className="form-group">
                    <label>Номер карты</label>
                    <input type="text" placeholder="0000 0000 0000 0000" className="form-input" required maxLength={19} />
                  </div>
                  <div className="card-form-row">
                    <div className="form-group">
                      <label>Срок действия</label>
                      <input type="text" placeholder="ММ/ГГ" className="form-input" required maxLength={5} />
                    </div>
                  </div>
                  <button type="submit" className="primary-button checkout-pay-btn">
                    Оплатить {formatPrice(displayTotal)}
                  </button>
                  <p className="secure-badge"><ShieldCheck size={14} /> Платеж защищен шифрованием</p>
                </form>
              )}
            </div>
          )}
        </div>

        {/* Sidebar Summary */}
        <div className="checkout-sidebar">
          <div className="checkout-summary glass-card">
            <h3>Ваш заказ</h3>
            <div className="checkout-items">
              {displayItems.map((item) => (
               <div key={item.id} className="checkout-item">
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
                <span className="checkout-final-price">{formatPrice(displayTotal)}</span>
              </div>
            </div>

            {step === 2 && paymentMethod !== 'CARD' && (
               <button className="primary-button checkout-pay-btn" onClick={handlePayment}>
                 Оплатить через {paymentMethod}
               </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
