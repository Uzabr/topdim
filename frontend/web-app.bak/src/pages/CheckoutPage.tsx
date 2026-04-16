import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CreditCard, Shield, CheckCircle } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { useAuthStore } from '../store/authStore';
import { ordersApi } from '../api/orders';
import { formatPrice } from '../utils/format';
import './CheckoutPage.css';

export default function CheckoutPage() {
  const navigate = useNavigate();
  const { items, totalPrice } = useCartStore();
  const { user } = useAuthStore();

  const [email, setEmail] = useState(user?.email || '');
  const [phone, setPhone] = useState(user?.phone || '');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isSuccess, setIsSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      await ordersApi.createOrder(email, phone);
      setIsSuccess(true);
    } catch {
      alert('Ошибка при создании заказа');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isSuccess) {
    return (
      <div className="checkout-page">
        <div className="checkout-success container">
          <CheckCircle size={64} className="checkout-success__icon" />
          <h2>Заказ оформлен!</h2>
          <p>Вы получите купоны на {email}</p>
          <button onClick={() => navigate('/profile')} className="checkout-success__btn">
            Мои купоны
          </button>
        </div>
      </div>
    );
  }

  if (items.length === 0) {
    return (
      <div className="checkout-page">
        <div className="checkout-success container">
          <h2>Корзина пуста</h2>
          <button onClick={() => navigate('/coupons')} className="checkout-success__btn">
            К купонам
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="checkout-page">
      <div className="checkout-content container">
        <div className="checkout-main">
          <h1 className="checkout-title">Оформление заказа</h1>

          <form onSubmit={handleSubmit} className="checkout-form">
            <div className="checkout-section">
              <h2 className="checkout-section-title">Контактные данные</h2>
              <div className="checkout-field">
                <label>Email</label>
                <input
                  type="email"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="your@email.com"
                  required
                />
              </div>
              <div className="checkout-field">
                <label>Телефон</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="+998 90 123 45 67"
                  required
                />
              </div>
            </div>

            <div className="checkout-section">
              <h2 className="checkout-section-title">
                <CreditCard size={20} /> Способ оплаты
              </h2>
              <div className="checkout-payments">
                <label className="checkout-payment checkout-payment--active">
                  <input type="radio" name="payment" defaultChecked />
                  <span>💳 Payme</span>
                </label>
                <label className="checkout-payment">
                  <input type="radio" name="payment" />
                  <span>💳 Click</span>
                </label>
                <label className="checkout-payment">
                  <input type="radio" name="payment" />
                  <span>💳 Uzum</span>
                </label>
              </div>
            </div>

            <button type="submit" className="checkout-submit" disabled={isSubmitting}>
              {isSubmitting ? 'Обработка...' : `Оплатить ${formatPrice(totalPrice)}`}
            </button>

            <div className="checkout-secure">
              <Shield size={14} /> Безопасная оплата
            </div>
          </form>
        </div>

        <aside className="checkout-sidebar glass">
          <h2>Ваш заказ</h2>
          <div className="checkout-items">
            {items.map((item) => (
              <div key={item.id} className="checkout-item">
                <div>
                  <div className="checkout-item__title">{item.couponTitle}</div>
                  <div className="checkout-item__option">{item.optionTitle} × {item.quantity}</div>
                </div>
                <span className="checkout-item__price">{formatPrice(item.unitPrice * item.quantity)}</span>
              </div>
            ))}
          </div>
          <div className="checkout-total">
            <span>Итого</span>
            <span>{formatPrice(totalPrice)}</span>
          </div>
        </aside>
      </div>
    </div>
  );
}
