import { X, Trash2, ShoppingBag, ArrowRight, Plus, Minus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCartStore } from '../../store/cartStore';
import { formatPrice } from '../../utils/format';
import { useLocalePath } from '../../hooks/useLocalePath';
import './CartDrawer.css';

export default function CartDrawer() {
  const { t } = useTranslation();
  const { items, isOpen, closeCart, removeFromCart, updateQuantity, totalItems, totalPrice, error, clearError } = useCartStore();
  const navigate = useNavigate();
  const lp = useLocalePath();

  if (!isOpen) return null;

  const handleCheckout = () => {
    closeCart();
    navigate(lp('/checkout'));
  };

  return (
    <>
      <div className="cart-overlay" onClick={closeCart} />
      <div className="cart-drawer glass">
        <div className="cart-drawer__header">
          <h2>
            <ShoppingBag size={20} />
            {t('cart.title')}
            {totalItems > 0 && <span className="cart-drawer__count">{totalItems}</span>}
          </h2>
          <button className="cart-drawer__close" onClick={closeCart} aria-label={t('common.close')}>
            <X size={24} />
          </button>
        </div>

        {items.length === 0 ? (
          <div className="cart-drawer__empty">
            <span className="cart-drawer__empty-icon">🛒</span>
            <h3>{t('cart.emptyTitle')}</h3>
            <p>{t('cart.emptyDesc')}</p>
            <button className="primary-button cart-drawer__empty-btn" onClick={() => { closeCart(); navigate(lp('/coupons')); }}>
              {t('cart.startShopping')}
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
                      <span className="cart-drawer__item-badge">🎁 {t('cart.gift')}</span>
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
                        aria-label={t('common.decrease')}
                      >
                        <Minus size={14} />
                      </button>
                      <span className="cart-drawer__qty-value">{item.quantity}</span>
                      <button
                        className="cart-drawer__qty-btn"
                        onClick={() => updateQuantity(item.key, item.quantity + 1)}
                        aria-label={t('common.increase')}
                      >
                        <Plus size={14} />
                      </button>
                    </div>
                    <button
                      className="cart-drawer__item-remove"
                      onClick={() => removeFromCart(item.key)}
                      aria-label={t('common.delete')}
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
                <span>{t('cart.totalToPay')}</span>
                <span className="cart-drawer__total-amount">{formatPrice(totalPrice)}</span>
              </div>
              <button className="primary-button cart-drawer__checkout-btn" onClick={handleCheckout}>
                {t('cart.checkout')}
                <ArrowRight size={18} />
              </button>
            </div>
          </>
        )}
      </div>
    </>
  );
}
