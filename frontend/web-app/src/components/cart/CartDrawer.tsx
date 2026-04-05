import { useState } from 'react';
import { X, Trash2, ShoppingBag, ArrowRight } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useCartStore } from '../../store/cartStore';
import { formatPrice } from '../../utils/format';
import { useAuthStore } from '../../store/authStore';
import './CartDrawer.css';

export default function CartDrawer() {
  const [cartTab, setCartTab] = useState<'COUPONS' | 'GOODS'>('COUPONS');
  const { items, isOpen, closeCart, removeFromCart, totalItems, totalPrice } = useCartStore();
  const { isAuthenticated } = useAuthStore();
  const navigate = useNavigate();

  const filteredItems = items.filter(item => cartTab === 'COUPONS' ? item.couponTitle : !item.couponTitle);

  if (!isOpen) return null;

  const handleCheckout = () => {
    closeCart();
    if (isAuthenticated) {
      navigate('/checkout');
    } else {
      // 1-click checkout for guests
      navigate('/checkout?guest=true');
    }
  };

  return (
    <>
      <div className="cart-overlay" onClick={closeCart} />
      <div className="cart-drawer glass">
        <div className="cart-drawer__header">
          <h2>
            <ShoppingBag size={20} />
            Корзина
            {totalItems > 0 && <span className="cart-drawer__count">{totalItems}</span>}
          </h2>
          <button className="cart-drawer__close" onClick={closeCart} aria-label="Закрыть">
            <X size={24} />
          </button>
        </div>

        {items.length === 0 ? (
          <div className="cart-drawer__empty">
            <span className="cart-drawer__empty-icon">🛒</span>
            <h3>Корзина пуста</h3>
            <p>Добавьте купоны или товары из каталога, чтобы начать покупки</p>
            <button className="primary-button cart-drawer__empty-btn" onClick={closeCart}>
              Начать покупки
            </button>
          </div>
        ) : (
          <>
            <div className="cart-drawer__tabs">
              <button 
                className={`cart-drawer__tab ${cartTab === 'COUPONS' ? 'cart-drawer__tab--active' : ''}`}
                onClick={() => setCartTab('COUPONS')}
              >
                Купоны
              </button>
              <button 
                className={`cart-drawer__tab ${cartTab === 'GOODS' ? 'cart-drawer__tab--active' : ''}`}
                onClick={() => setCartTab('GOODS')}
              >
                Товары
              </button>
            </div>
          
            <div className="cart-drawer__items">
              {filteredItems.length === 0 ? (
                <div className="cart-drawer__empty-tab">
                  В этой категории пока ничего нет
                </div>
              ) : (
                filteredItems.map((item) => (
                  <div key={item.id} className="cart-drawer__item">
                    <div className="cart-drawer__item-info">
                      <h4 className="cart-drawer__item-title">{item.couponTitle || item.optionTitle}</h4>
                      {item.couponTitle && <p className="cart-drawer__item-option">{item.optionTitle}</p>}
                      {item.gift && (
                        <span className="cart-drawer__item-badge">🎁 В подарок</span>
                      )}
                    </div>
                    <div className="cart-drawer__item-meta">
                      <div className="cart-drawer__item-price-block">
                        <span className="cart-drawer__item-price">
                          {formatPrice(item.unitPrice * item.quantity)}
                        </span>
                        <span className="cart-drawer__item-qty">{item.quantity} шт.</span>
                      </div>
                      <button
                        className="cart-drawer__item-remove"
                        onClick={() => removeFromCart(item.id)}
                        aria-label="Удалить"
                      >
                        <Trash2 size={16} />
                      </button>
                    </div>
                  </div>
                ))
              )}
            </div>

            <div className="cart-drawer__footer">
              <div className="cart-drawer__total">
                <span>Итого к оплате:</span>
                <span className="cart-drawer__total-amount">{formatPrice(totalPrice)}</span>
              </div>
              <button className="primary-button cart-drawer__checkout-btn" onClick={handleCheckout}>
                Оформить заказ
                <ArrowRight size={18} />
              </button>
            </div>
          </>
        )}
      </div>
    </>
  );
}
