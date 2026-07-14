import { useState } from 'react';
import { ChevronLeft, Minus, Plus, ShoppingCart, X } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useCartStore } from '../store/cartStore';
import { useLocalePath } from '../hooks/useLocalePath';
import { formatPrice } from '../utils/format';
import './CartMobile.css';

/** Строка уезжает влево и только потом исчезает — иначе список «прыгает». */
const LEAVE_MS = 300;

export default function CartMobile() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { items, totalItems, totalPrice, removeFromCart, updateQuantity } = useCartStore();

  const [leaving, setLeaving] = useState<Record<string, boolean>>({});

  const remove = (key: string) => {
    setLeaving((prev) => ({ ...prev, [key]: true }));
    setTimeout(() => {
      removeFromCart(key);
      setLeaving((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }, LEAVE_MS);
  };

  return (
    <div className="cmcart">
      <div className="mbar cmcart__bar">
        <button
          type="button"
          className="mround"
          onClick={() => (window.history.length > 1 ? navigate(-1) : navigate(lp('/')))}
          aria-label={t('common.back')}
        >
          <ChevronLeft size={18} strokeWidth={2} />
        </button>

        <span className="mbar__title">{t('cart.title')}</span>

        <span className="cmcart__count">{totalItems || ''}</span>
      </div>

      {items.length === 0 ? (
        <div className="cmcart__empty">
          <span className="cmcart__empty-icon">
            <ShoppingCart size={32} strokeWidth={1.6} />
          </span>
          <h1 className="cmcart__empty-title">{t('cart.emptyTitle')}</h1>
          <p className="cmcart__empty-text">{t('cart.emptyDesc')}</p>
          <Link to={lp('/coupons')} className="cmcart__empty-btn">
            {t('cart.toCoupons')}
          </Link>
        </div>
      ) : (
        <>
          <div className="cmcart__list">
            {items.map((item) => (
              <article
                key={item.key}
                className={`crow${leaving[item.key] ? ' crow--out' : ''}`}
              >
                <div className="crow__photo">
                  {item.coverImageUrl && <img src={item.coverImageUrl} alt="" loading="lazy" />}
                </div>

                <div className="crow__body">
                  <div className="crow__top">
                    <h2 className="crow__title">{item.couponTitle}</h2>
                    <button
                      type="button"
                      className="crow__remove"
                      onClick={() => remove(item.key)}
                      aria-label={t('common.delete')}
                    >
                      <X size={13} />
                    </button>
                  </div>

                  <p className="crow__sub">{item.optionTitle}</p>

                  <div className="crow__foot">
                    <span className="crow__sum">{formatPrice(item.unitPrice * item.quantity)}</span>

                    <span className="crow__qty">
                      <button
                        type="button"
                        className="crow__step"
                        onClick={() =>
                          item.quantity > 1
                            ? updateQuantity(item.key, item.quantity - 1)
                            : remove(item.key)
                        }
                        aria-label={t('common.decrease')}
                      >
                        <Minus size={13} />
                      </button>
                      <span className="crow__num">{item.quantity}</span>
                      <button
                        type="button"
                        className="crow__step"
                        onClick={() => updateQuantity(item.key, item.quantity + 1)}
                        aria-label={t('common.increase')}
                      >
                        <Plus size={13} />
                      </button>
                    </span>
                  </div>
                </div>
              </article>
            ))}
          </div>

          {/* «Экономии» из макета нет: корзина не хранит старую цену — ни локальная,
              ни серверная, и выдумывать её мы не будем. */}
          <div className="cmcart__total">
            <div className="cmcart__row">
              <span>
                {t('cart.itemsLine', { count: totalItems })}
              </span>
              <span className="cmcart__row-value">{formatPrice(totalPrice)}</span>
            </div>

            <div className="cmcart__grand">
              <span>{t('cart.summary')}</span>
              <span>{formatPrice(totalPrice)}</span>
            </div>

            <button
              type="button"
              className="cmcart__cta"
              onClick={() => navigate(lp('/checkout'))}
            >
              {t('cart.checkout')}
            </button>
          </div>
        </>
      )}
    </div>
  );
}
