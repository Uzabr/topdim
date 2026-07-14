import { Link, useNavigate } from 'react-router-dom';
import { Trash2, ShoppingBag, ArrowRight, Plus, Minus, ShoppingCart, Ticket } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { useCartStore } from '../store/cartStore';
import { formatPrice } from '../utils/format';
import { useLocalePath } from '../hooks/useLocalePath';
import './CartPage.css';

export default function CartDesktop() {
  const { t } = useTranslation();
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
          <ShoppingCart className="cart-empty__icon" size={64} strokeWidth={1.25} />
          <h2>{t('cart.emptyTitle')}</h2>
          <p>{t('cart.emptyDesc')}</p>
          <Link to={lp('/coupons')} className="primary-button cart-empty__btn">
            <ShoppingBag size={18} /> {t('cart.toCoupons')}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="cart-page">
      <div className="cart-header container">
        <h1 className="cart-title">{t('cart.title')}</h1>
        <span className="cart-count badge">{t('cart.itemsCount', { count: totalItems })}</span>
      </div>

      <div className="cart-content container">
        <div className="cart-items surface-card">
          {items.map((item) => (
            <div key={item.key} className="cart-item">
              <div className="cart-item__icon-wrapper">
                {item.coverImageUrl ? (
                  <img src={item.coverImageUrl} alt="" className="cart-item__img" />
                ) : (
                  <div className="cart-item__icon"><Ticket size={26} strokeWidth={1.5} /></div>
                )}
              </div>
              <div className="cart-item__info">
                <h3 className="cart-item__title">{item.couponTitle}</h3>
                <p className="cart-item__option">{item.optionTitle}</p>
                {item.isGift && (
                  <span className="cart-item__gift badge">
                    {t('cart.gift')}{item.giftRecipientName ? ` (${t('cart.giftTo', { name: item.giftRecipientName })})` : ''}
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
                  aria-label={t('common.delete')}
                >
                  <Trash2 size={18} />
                </button>
              </div>
            </div>
          ))}
        </div>

        <div className="cart-summary surface-card">
          <h2>{t('cart.summary')}</h2>
          <div className="cart-summary__row">
            <span>{t('cart.itemsLine', { count: totalItems })}</span>
            <span className="cart-summary__value">{formatPrice(totalPrice)}</span>
          </div>
          <div className="cart-summary__total">
            <span>{t('cart.toPay')}</span>
            <span className="cart-summary__price">{formatPrice(totalPrice)}</span>
          </div>
          <button onClick={handleCheckout} className="primary-button cart-summary__btn">
            {t('cart.checkout')} <ArrowRight size={18} />
          </button>
        </div>
      </div>
    </div>
  );
}
