import { useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { ShieldCheck, ChevronLeft, CreditCard, Apple, Smartphone, AlertCircle } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import { useAuthStore } from '../store/authStore';
import './CheckoutPage.css';

export default function CheckoutPage() {
  const [searchParams] = useSearchParams();
  const isGuest = searchParams.get('guest') === 'true';
  const couponId = searchParams.get('couponId');
  const optionId = searchParams.get('optionId'); // 1-click direct link case
  
  const { items, totalPrice, clearCart } = useCartStore();
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  const [step, setStep] = useState(isGuest && !isAuthenticated ? 1 : 2);
  const [phone, setPhone] = useState('');
  const [name, setName] = useState('');
  const [smsCode, setSmsCode] = useState('');
  const [isSmsSent, setIsSmsSent] = useState(false);
  const [paymentMethod, setPaymentMethod] = useState<'CARD' | 'CLICK' | 'PAYME'>('CARD');

  // If we came from 1-click buy, we might have no items in cart but a direct coupon.
  // For now, assume cart has items or we mock it.
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

  const handleSendSms = (e: React.FormEvent) => {
    e.preventDefault();
    if (phone.length > 8 && name) {
      setIsSmsSent(true);
    }
  };

  const handleVerifySms = (e: React.FormEvent) => {
    e.preventDefault();
    if (smsCode === '0000') { // Mock OTP
      setStep(2);
    } else {
      alert('Неверный код (введите 0000 для теста)');
    }
  };

  const handlePayment = () => {
    alert('Mock: Оплата успешна! Купон отправлен в ваш профиль (и SMS).');
    clearCart();
    navigate('/profile');
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
              
              {!isSmsSent ? (
                <form className="checkout-form" onSubmit={handleSendSms}>
                  <div className="form-group">
                    <label>Имя</label>
                    <input 
                      type="text" 
                      placeholder="Иван Иванов" 
                      className="form-input"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      required 
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
                      />
                    </div>
                  </div>
                  <button type="submit" className="primary-button checkout-next-btn">
                    Получить SMS-код
                  </button>
                </form>
              ) : (
                <form className="checkout-form" onSubmit={handleVerifySms}>
                  <div className="form-group">
                    <label>Код из SMS</label>
                    <p className="form-hint">Мы отправили код на +998 {phone}</p>
                    <input 
                      type="text" 
                      placeholder="0000" 
                      className="form-input form-input--center"
                      maxLength={4}
                      value={smsCode}
                      onChange={(e) => setSmsCode(e.target.value.replace(/\D/g, ''))}
                      required 
                    />
                  </div>
                  <button type="submit" className="primary-button checkout-next-btn">
                    Подтвердить и продолжить
                  </button>
                  <button type="button" className="text-button checkout-edit-btn" onClick={() => setIsSmsSent(false)}>
                    Изменить номер
                  </button>
                </form>
              )}
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
