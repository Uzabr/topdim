import { useState } from 'react';
import { X, Trash2, ShoppingBag } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useCartStore } from '../../store/cartStore';
import './CartDrawer.css';

export default function CartDrawer() {
  const [cartTab, setCartTab] = useState<'COUPONS' | 'GOODS'>('COUPONS');
  const { items, isOpen, closeCart, removeFromCart, totalItems, totalPrice } = useCartStore();

  const filteredItems = items.filter(item => cartTab === 'COUPONS' ? item.couponTitle : !item.couponTitle);

  if (!isOpen) return null;

  return (
    <>
      <div className="cart-overlay" onClick={closeCart} />
      <div className="cart-drawer glass">
        <div className="cart-drawer__header">
          <h2>
            <ShoppingBag size={20} />
            Корзина ({totalItems})
          </h2>
          <button className="cart-drawer__close" onClick={closeCart}>
            <X size={24} />
          </button>
        </div>

        {items.length === 0 ? (
          <div className="cart-drawer__empty">
            <span className="cart-empty-icon">🛒</span>
            <p>Корзина пуста</p>
            <p className="cart-empty-hint">Добавьте купоны или товары из каталога</p>
            <button className="cart-empty-btn" onClick={closeCart}>
              Перейти к покупкам
            </button>
          </div>
        ) : (
          <>
            <div className="cart-drawer-tabs" style={{ display: 'flex', gap: '8px', padding: '0 20px 16px' }}>
              <button 
                style={{ flex: 1, padding: '10px', borderRadius: '12px', background: cartTab === 'COUPONS' ? 'var(--primary-strong)' : 'transparent', color: cartTab === 'COUPONS' ? 'white' : 'var(--text-secondary)', border: '1px solid var(--border)' }}
                onClick={() => setCartTab('COUPONS')}
              >
                Купоны
              </button>
              <button 
                style={{ flex: 1, padding: '10px', borderRadius: '12px', background: cartTab === 'GOODS' ? 'var(--primary-strong)' : 'transparent', color: cartTab === 'GOODS' ? 'white' : 'var(--text-secondary)', border: '1px solid var(--border)' }}
                onClick={() => setCartTab('GOODS')}
              >
                Товары
              </button>
            </div>
          
            <div className="cart-drawer__items">
              {filteredItems.map((item) => (
                <div key={item.id} className="cart-item">
                  <div className="cart-item__info">
                    <h4 className="cart-item__title">{item.couponTitle}</h4>
                    <p className="cart-item__option">{item.optionTitle}</p>
                    {item.gift && (
                      <span className="cart-item__gift">🎁 Подарок для {item.giftRecipientName}</span>
                    )}
                  </div>
                  <div className="cart-item__actions">
                    <span className="cart-item__price">
                      {(item.unitPrice * item.quantity).toLocaleString()} сум
                    </span>
                    <span className="cart-item__qty">x{item.quantity}</span>
                    <button
                      className="cart-item__remove"
                      onClick={() => removeFromCart(item.id)}
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>

            <div className="cart-drawer__footer">
              <div className="cart-total">
                <span>Итого:</span>
                <span className="cart-total__amount">{totalPrice.toLocaleString()} сум</span>
              </div>
              <Link to="/checkout" className="cart-checkout-btn" onClick={closeCart}>
                Оформить заказ
              </Link>
            </div>
          </>
        )}
      </div>
    </>
  );
}
