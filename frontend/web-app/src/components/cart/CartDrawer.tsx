import { X, Trash2, ShoppingBag, ArrowRight, Plus, Minus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useCartStore } from '../../store/cartStore';
import { formatPrice } from '../../utils/format';
import './CartDrawer.css';

export default function CartDrawer() {
  const { items, isOpen, closeCart, removeFromCart, updateQuantity, totalItems, totalPrice, error, clearError } = useCartStore();
  const navigate = useNavigate();

  if (!isOpen) return null;

  const handleCheckout = () => {
    closeCart();
    navigate('/checkout');
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
            <p>Добавьте купоны из каталога, чтобы начать покупки</p>
            <button className="primary-button cart-drawer__empty-btn" onClick={() => { closeCart(); navigate('/coupons'); }}>
              Начать покупки
            </button>
          </div>
        ) : (
          <>
            <div className="cart-drawer__items">
              {items.map((item) => (
                <div key={item.key} className="cart-drawer__item">
                  {item.coverImageUrl && (
                    <div className="cart-drawer__item-img">
                      <img src={item.coverImageUrl} alt="" />
                    </div>
                  )}
                  <div className="cart-drawer__item-info">
                    <h4 className="cart-drawer__item-title">{item.couponTitle}</h4>
                    <p className="cart-drawer__item-option">{item.optionTitle}</p>
                    {item.isGift && (
                      <span className="cart-drawer__item-badge">🎁 В подарок</span>
                    )}
                  </div>
                  <div className="cart-drawer__item-meta">
                    <span className="cart-drawer__item-price">
                      {formatPrice(item.unitPrice * item.quantity)}
                    </span>
                    <div className="cart-drawer__item-qty-controls">
                      <button
                        className="cart-drawer__qty-btn"
                        onClick={() => updateQuantity(item.key, item.quantity - 1)}
                        aria-label="Уменьшить"
                      >
                        <Minus size={14} />
                      </button>
                      <span className="cart-drawer__qty-value">{item.quantity}</span>
                      <button
                        className="cart-drawer__qty-btn"
                        onClick={() => updateQuantity(item.key, item.quantity + 1)}
                        aria-label="Увеличить"
                      >
                        <Plus size={14} />
                      </button>
                    </div>
                    <button
                      className="cart-drawer__item-remove"
                      onClick={() => removeFromCart(item.key)}
                      aria-label="Удалить"
                    >
                      <Trash2 size={16} />
                    </button>
                  </div>
                </div>
              ))}
            </div>

            <div className="cart-drawer__footer">
              {error && (
                <div className="cart-drawer__error" role="alert" onClick={clearError}>
                  {error}
                </div>
              )}
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
