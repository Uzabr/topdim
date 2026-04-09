import { Link, useNavigate } from 'react-router-dom';
import { Trash2, ShoppingBag, ArrowRight, Plus, Minus } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import { useLocalePath } from '../hooks/useLocalePath';
import './CartPage.css';

export default function CartPage() {
  const { items, totalItems, totalPrice, removeFromCart, updateQuantity } = useCartStore();
  const navigate = useNavigate();
  const lp = useLocalePath();

  const handleCheckout = () => {
    navigate(lp('/checkout'));
  };

  if (items.length === 0) {
    return (
      <div className="cart-page">
        <div className="cart-empty container">
          <div className="cart-empty__icon">🛒</div>
          <h2>Корзина пуста</h2>
          <p>Добавьте купоны из каталога, чтобы начать покупки</p>
          <Link to={lp('/coupons')} className="primary-button cart-empty__btn">
            <ShoppingBag size={18} /> К купонам
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="cart-page">
      <div className="cart-header container">
        <h1 className="cart-title">Корзина</h1>
        <span className="cart-count badge">{totalItems} товар(ов)</span>
      </div>

      <div className="cart-content container">
        <div className="cart-items glass-card">
          {items.map((item) => (
            <div key={item.key} className="cart-item">
              <div className="cart-item__icon-wrapper">
                {item.coverImageUrl ? (
                  <img src={item.coverImageUrl} alt="" className="cart-item__img" />
                ) : (
                  <div className="cart-item__icon">🎫</div>
                )}
              </div>
              <div className="cart-item__info">
                <h3 className="cart-item__title">{item.couponTitle}</h3>
                <p className="cart-item__option">{item.optionTitle}</p>
                {item.isGift && (
                  <span className="cart-item__gift badge">
                    🎁 В подарок {item.giftRecipientName ? `(Кому: ${item.giftRecipientName})` : ''}
                  </span>
                )}
                <div className="cart-item__qty">
                  <button
                    className="cart-item__qty-btn"
                    onClick={() => updateQuantity(item.key, item.quantity - 1)}
                  >
                    <Minus size={14} />
                  </button>
                  <span>{item.quantity}</span>
                  <button
                    className="cart-item__qty-btn"
                    onClick={() => updateQuantity(item.key, item.quantity + 1)}
                  >
                    <Plus size={14} />
                  </button>
                </div>
              </div>
              <div className="cart-item__right">
                <span className="cart-item__price">
                  {formatPrice(item.unitPrice * item.quantity)}
                </span>
                <button
                  className="icon-button cart-item__remove"
                  onClick={() => removeFromCart(item.key)}
                  aria-label="Удалить"
                >
                  <Trash2 size={18} />
                </button>
              </div>
            </div>
          ))}
        </div>

        <div className="cart-summary glass-card">
          <h2>Итого</h2>
          <div className="cart-summary__row">
            <span>Товары ({totalItems})</span>
            <span className="cart-summary__value">{formatPrice(totalPrice)}</span>
          </div>
          <div className="cart-summary__total">
            <span>К оплате</span>
            <span className="cart-summary__price">{formatPrice(totalPrice)}</span>
          </div>
          <button onClick={handleCheckout} className="primary-button cart-summary__btn">
            Оформить заказ <ArrowRight size={18} />
          </button>
        </div>
      </div>
    </div>
  );
}
