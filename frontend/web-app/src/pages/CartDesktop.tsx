import { ArrowRight, Minus, Plus, ShoppingCart, Ticket, X } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { couponsApi } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import { useCartStore } from '../store/cartStore';
import { useLocalePath } from '../hooks/useLocalePath';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
import { formatPrice } from '../utils/format';
import './CartPage.css';

/** Сколько купонов показываем в «Возможно, вас заинтересует». */
const RECS = 4;

export default function CartDesktop() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const { items, totalItems, totalPrice, removeFromCart, updateQuantity } = useCartStore();

  // Рекомендации — популярное; из ленты вычитаем то, что уже в корзине.
  const { data: recs = [] } = useQuery({
    queryKey: ['cart-recs'],
    queryFn: () => couponsApi.getCatalog({ sortBy: 'popular', size: RECS + items.length }),
    select: (res) => res.data.data.content,
  });

  const inCart = new Set(items.map((i) => i.couponOfferId));
  const recommendations = recs.filter((c) => !inCart.has(c.id)).slice(0, RECS);

  if (items.length === 0) {
    return (
      <div className="cart container">
        <div className="cart__empty">
          <span className="cart__empty-icon">
            <ShoppingCart size={34} strokeWidth={1.6} />
          </span>
          <h1 className="cart__empty-title">{t('cart.emptyTitle')}</h1>
          <p className="cart__empty-text">{t('cart.emptyDesc')}</p>
          <Link to={lp('/coupons')} className="cart__empty-btn">
            {t('cart.toCoupons')}
          </Link>
        </div>

        {recommendations.length > 0 && (
          <section className="cart__recs">
            <h2 className="cart__recs-title">{t('cart.recommendations')}</h2>
            <div className="cart__recs-grid">
              {recommendations.map((c) => (
                <CouponCard key={c.id} coupon={mapCouponOfferToCardData(c)} />
              ))}
            </div>
          </section>
        )}
      </div>
    );
  }

  return (
    <div className="cart container">
      <header className="cart__head">
        <h1 className="cart__title">{t('cart.title')}</h1>
        <span className="cart__count">{t('cart.itemsCount', { count: totalItems })}</span>
      </header>

      <div className="cart__grid">
        <div className="cart__items">
          {items.map((item) => (
            <article key={item.key} className="citem">
              <div className="citem__photo">
                {item.coverImageUrl ? (
                  <img src={item.coverImageUrl} alt="" loading="lazy" />
                ) : (
                  <Ticket size={26} strokeWidth={1.5} className="citem__ph" />
                )}
              </div>

              <div className="citem__body">
                <div className="citem__top">
                  <h2 className="citem__title">{item.couponTitle}</h2>
                  <button
                    type="button"
                    className="citem__remove"
                    onClick={() => removeFromCart(item.key)}
                    aria-label={t('common.delete')}
                  >
                    <X size={15} />
                  </button>
                </div>

                <p className="citem__option">{item.optionTitle}</p>

                {item.isGift && (
                  <span className="citem__gift">
                    {t('cart.gift')}
                    {item.giftRecipientName
                      ? ` · ${t('cart.giftTo', { name: item.giftRecipientName })}`
                      : ''}
                  </span>
                )}

                <div className="citem__foot">
                  <span className="citem__qty">
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
                      <Minus size={14} />
                    </button>
                    <span className="citem__num">{item.quantity}</span>
                    <button
                      type="button"
                      className="citem__step"
                      onClick={() => updateQuantity(item.key, item.quantity + 1)}
                      aria-label={t('common.increase')}
                    >
                      <Plus size={14} />
                    </button>
                  </span>

                  <span className="citem__price">
                    {formatPrice(item.unitPrice * item.quantity)}
                  </span>
                </div>
              </div>
            </article>
          ))}
        </div>

        <aside className="cart__summary">
          <h2 className="cart__summary-title">{t('cart.summary')}</h2>

          <div className="cart__row">
            <span>{t('cart.itemsLine', { count: totalItems })}</span>
            <span className="cart__row-value">{formatPrice(totalPrice)}</span>
          </div>

          <div className="cart__total">
            <span>{t('cart.toPay')}</span>
            <span>{formatPrice(totalPrice)}</span>
          </div>

          <button
            type="button"
            className="cart__checkout"
            onClick={() => navigate(lp('/checkout'))}
          >
            {t('cart.checkout')}
            <ArrowRight size={18} />
          </button>
        </aside>
      </div>

      {recommendations.length > 0 && (
        <section className="cart__recs">
          <h2 className="cart__recs-title">{t('cart.recommendations')}</h2>
          <div className="cart__recs-grid">
            {recommendations.map((c) => (
              <CouponCard key={c.id} coupon={mapCouponOfferToCardData(c)} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}
