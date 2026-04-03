import { Link } from 'react-router-dom';
import { Trash2, ShoppingBag, ArrowRight } from 'lucide-react';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import './CartPage.css';

export default function CartPage() {
  const { items, totalItems, totalPrice, removeFromCart } = useCartStore();

  if (items.length === 0) {
    return (
      <div className="cart-page">
        <div className="cart-empty container">
          <div className="cart-empty__icon">🛒</div>
          <h2>Корзина пуста</h2>
          <p>Добавьте купоны из каталога</p>
          <Link to="/coupons" className="cart-empty__btn">
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
        <span className="cart-count">{totalItems} товар(ов)</span>
      </div>

      <div className="cart-content container">
        <div className="cart-items">
          {items.map((item) => (
            <div key={item.id} className="cart-item">
              <div className="cart-item__icon">🎫</div>
              <div className="cart-item__info">
                <h3 className="cart-item__title">{item.couponTitle}</h3>
                <p className="cart-item__option">{item.optionTitle}</p>
                {item.gift && (
                  <p className="cart-item__gift">
                    🎁 Подарок: {item.giftRecipientName}
                  </p>
                )}
                <div className="cart-item__qty">
                  <span>Кол-во: {item.quantity}</span>
                </div>
              </div>
              <div className="cart-item__right">
                <span className="cart-item__price">
                  {formatPrice(item.unitPrice * item.quantity)}
                </span>
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

        <div className="cart-summary glass">
          <h2>Итого</h2>
          <div className="cart-summary__row">
            <span>Товары ({totalItems})</span>
            <span>{formatPrice(totalPrice)}</span>
          </div>
          <div className="cart-summary__total">
            <span>К оплате</span>
            <span className="cart-summary__price">{formatPrice(totalPrice)}</span>
          </div>
          <Link to="/checkout" className="cart-summary__btn">
            Оформить заказ <ArrowRight size={18} />
          </Link>
        </div>
      </div>
    </div>
  );
}
