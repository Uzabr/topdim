import { X, Trash2, ShoppingBag, ArrowRight, Plus, Minus, Heart, ShoppingCart, Ticket } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCartStore } from '../../store/cartStore';
import { useFavoritesStore } from '../../store/favoritesStore';
import ShareButton from '../ui/ShareButton';
import { formatPrice } from '../../utils/format';
import { useLocalePath } from '../../hooks/useLocalePath';
import './CartDrawer.css';

export default function CartDrawer() {
  const { t } = useTranslation();
  const { items, isOpen, closeCart, removeFromCart, updateQuantity, totalItems, totalPrice, error, clearError } = useCartStore();
  const { toggleFavorite, isFavorite } = useFavoritesStore();
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
      <div className="cart-drawer">
        <div className="cart-drawer__header">
          <h2>
            <ShoppingBag size={20} />
            {t('cart.title')}
            {totalItems > 0 && <span className="cart-drawer__count">{totalItems}</span>}
          </h2>
          <button className="cart-drawer__close" onClick={closeCart} aria-label={t('common.close')}>
            <X size={28} strokeWidth={2} />
          </button>
        </div>

        {items.length === 0 ? (
          <div className="cart-drawer__empty">
            <ShoppingCart className="cart-drawer__empty-icon" size={56} strokeWidth={1.25} />
            <h3>{t('cart.emptyTitle')}</h3>
            <p>{t('cart.emptyDesc')}</p>
            <button className="primary-button cart-drawer__empty-btn" onClick={() => { closeCart(); navigate(lp('/coupons')); }}>
              {t('cart.startShopping')}
            </button>
          </div>
        ) : (
          <>
            <div className="cart-drawer__items">
              {items.map((item) => {
                const fav = isFavorite(item.couponOfferId);
                return (
                  <div key={item.key} className="cart-drawer__item">
                    <div className="cart-drawer__item-img">
                      {item.coverImageUrl ? (
                        <img src={item.coverImageUrl} alt="" />
                      ) : (
                        <span className="cart-drawer__item-img-placeholder"><Ticket size={22} strokeWidth={1.5} /></span>
                      )}
                    </div>

                    <div className="cart-drawer__item-body">
                      <div className="cart-drawer__item-toolbar">
                        <button
                          type="button"
                          className={`cart-drawer__toolbar-btn${fav ? ' cart-drawer__toolbar-btn--active' : ''}`}
                          onClick={() => toggleFavorite(item.couponOfferId)}
                          aria-label={t('couponDetail.favorite')}
                        >
                          <Heart size={18} fill={fav ? 'currentColor' : 'none'} />
                        </button>
                        <ShareButton
                          title={item.couponTitle}
                          text={item.optionTitle}
                          variant="icon"
                        />
                        <button
                          type="button"
                          className="cart-drawer__toolbar-btn cart-drawer__toolbar-btn--danger"
                          onClick={() => removeFromCart(item.key)}
                          aria-label={t('common.delete')}
                        >
                          <Trash2 size={18} />
                        </button>
                      </div>

                      <div className="cart-drawer__item-text">
                        <h4 className="cart-drawer__item-title">{item.couponTitle}</h4>
                        <p className="cart-drawer__item-option">{item.optionTitle}</p>
                        {item.isGift && (
                          <span className="cart-drawer__item-badge">{t('cart.gift')}</span>
                        )}
                      </div>

                      <div className="cart-drawer__item-row-bottom">
                        <div className="cart-drawer__item-qty-controls">
                          <button
                            type="button"
                            className="cart-drawer__qty-btn"
                            onClick={() => updateQuantity(item.key, item.quantity - 1)}
                            aria-label={t('common.decrease')}
                          >
                            <Minus size={14} />
                          </button>
                          <span className="cart-drawer__qty-value">{item.quantity}</span>
                          <button
                            type="button"
                            className="cart-drawer__qty-btn"
                            onClick={() => updateQuantity(item.key, item.quantity + 1)}
                            aria-label={t('common.increase')}
                          >
                            <Plus size={14} />
                          </button>
                        </div>
                        <span className="cart-drawer__item-price">
                          {formatPrice(item.unitPrice * item.quantity)}
                        </span>
                      </div>
                    </div>
                  </div>
                );
              })}
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
