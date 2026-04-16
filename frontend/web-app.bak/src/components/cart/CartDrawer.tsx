import { X, Trash2, ShoppingBag } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useCartStore } from '../../store/cartStore';
import './CartDrawer.css';

export default function CartDrawer() {
  const { items, isOpen, closeCart, removeFromCart, totalItems, totalPrice } = useCartStore();

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
            <p className="cart-empty-hint">Добавьте купоны из каталога</p>
            <button className="cart-empty-btn" onClick={closeCart}>
              Смотреть купоны
            </button>
          </div>
        ) : (
          <>
            <div className="cart-drawer__items">
              {items.map((item) => (
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
