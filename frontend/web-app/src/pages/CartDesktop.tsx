import { Minus, Plus, ShoppingCart, Ticket, X } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCartStore } from '../store/cartStore';
import { useLocalePath } from '../hooks/useLocalePath';
import { calcDiscount, formatPrice } from '../utils/format';
import './CartPage.css';

export default function CartDesktop() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { items, totalItems, totalPrice, removeFromCart, updateQuantity } = useCartStore();

  // Старую цену знаем не для всех позиций (backend-корзина и старые записи её не
  // хранят) — «Скидку» показываем, только если есть что показать.
  const oldTotal = items.reduce((sum, i) => sum + (i.oldPrice ?? i.unitPrice) * i.quantity, 0);
  const saving = oldTotal - totalPrice;

  const countLabel = t('cart.couponsCount', { count: totalItems });

  if (items.length === 0) {
    return (
      <div className="cart container">
        <h1 className="cart__title">{t('cart.title')}</h1>

        <div className="cart__empty">
          <span className="cart__empty-icon">
            <ShoppingCart size={46} strokeWidth={1.6} />
          </span>
          <h2 className="cart__empty-title">{t('cart.emptyTitle')}</h2>
          <p className="cart__empty-text">{t('cart.emptyHint')}</p>
          <Link to={lp('/coupons')} className="cart__empty-btn">
            {t('cart.toCoupons')}
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="cart container">
      <h1 className="cart__title">
        {t('cart.title')} <span className="cart__count">{countLabel}</span>
      </h1>

      <div className="cart__grid">
        <div className="cart__items">
          {items.map((item) => {
            const off = item.oldPrice ? calcDiscount(item.oldPrice, item.unitPrice) : 0;
            return (
              <article key={item.key} className="citem">
                <div className="citem__photo">
                  {item.coverImageUrl ? (
                    <img src={item.coverImageUrl} alt="" loading="lazy" />
                  ) : (
                    <Ticket size={24} strokeWidth={1.5} className="citem__ph" />
                  )}
                </div>

                <div className="citem__info">
                  <h2 className="citem__title">{item.couponTitle}</h2>
                  <p className="citem__variant">{item.optionTitle}</p>

                  <div className="citem__pricing">
                    <span className="citem__price">{formatPrice(item.unitPrice)}</span>
                    {item.oldPrice && item.oldPrice > item.unitPrice && (
                      <span className="citem__old">{item.oldPrice.toLocaleString('ru-RU')}</span>
                    )}
                    {off > 0 && <span className="citem__off">−{off}%</span>}
                  </div>

                  {item.isGift && (
                    <span className="citem__gift">
                      {t('cart.gift')}
                      {item.giftRecipientName
                        ? ` · ${t('cart.giftTo', { name: item.giftRecipientName })}`
                        : ''}
                    </span>
                  )}
                </div>

                <div className="citem__controls">
                  <div className="citem__stepper">
                    <button
                      type="button"
                      className="citem__step"
                      onClick={() =>
                        item.quantity > 1
                          ? updateQuantity(item.key, item.quantity - 1)
                          : removeFromCart(item.key)
                      }
                      aria-label={t('common.decrease')}
                    >
                      <Minus size={15} />
                    </button>
                    <span className="citem__num">{item.quantity}</span>
                    <button
                      type="button"
                      className="citem__step"
                      onClick={() => updateQuantity(item.key, item.quantity + 1)}
                      aria-label={t('common.increase')}
                    >
                      <Plus size={15} />
                    </button>
                  </div>

                  <button
                    type="button"
                    className="citem__remove"
                    onClick={() => removeFromCart(item.key)}
                    aria-label={t('common.delete')}
                  >
                    <X size={14} />
                  </button>
                </div>
              </article>
            );
          })}

          <p className="cart__note">{t('cart.deliveryNote')}</p>
        </div>

        <aside className="cart__summary">
          <h2 className="cart__summary-title">{t('cart.summary')}</h2>

          {saving > 0 && (
            <>
              <div className="cart__row">
                <span>{countLabel}</span>
                <span className="cart__num">{formatPrice(oldTotal)}</span>
              </div>
              <div className="cart__row">
                <span>{t('cart.discount')}</span>
                <span className="cart__saving">−{formatPrice(saving)}</span>
              </div>
            </>
          )}

          <div className="cart__total">
            <span>{t('cart.toPay')}</span>
            <span className="cart__num">{formatPrice(totalPrice)}</span>
          </div>

          <button
            type="button"
            className="cart__checkout"
            onClick={() => navigate(lp('/checkout'))}
          >
            {t('cart.checkout')}
          </button>
        </aside>
      </div>
    </div>
  );
}
