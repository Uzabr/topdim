import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Clock, MapPin, Phone, Gift, ShoppingCart, Star, TrendingUp, Calendar } from 'lucide-react';
import { couponsApi } from '../api/coupons';
import type { CouponOffer, CouponOption } from '../api/coupons';
import { useCartStore } from '../store/cartStore';
import { formatPrice, daysUntil, formatDate } from '../utils/format';
import './CouponDetailPage.css';

const DEMO_COUPON: CouponOffer = {
  id: 1, title: 'Скидка на пиццу в PizzaLab', shortDescription: 'Любая пицца 33 см + напиток',
  fullDescription: 'Отличное предложение от PizzaLab! Выберите любую пиццу диаметром 33 см из нашего меню и получите напиток на выбор совершенно бесплатно. Предложение действует во всех филиалах PizzaLab в Ташкенте.',
  merchant: { id: 1, name: 'PizzaLab', logoUrl: '' },
  category: { id: 1, name: 'Еда', slug: 'food' },
  oldPrice: 89000, fromPrice: 45000, discountPercent: 49, coverImageUrl: '',
  buyUntil: '2026-04-30T00:00:00', useUntil: '2026-05-30T00:00:00',
  terms: '1 купон на 1 человека в день', usageRules: 'Показать купон официанту перед заказом',
  howToUse: '1. Покажите купон\n2. Выберите пиццу\n3. Наслаждайтесь',
  address: 'Ташкент, Мирзо Улугбека, 55', contactPhone: '+998 90 123 45 67',
  workingHours: '10:00 – 22:00', giftAvailable: true, status: 'ACTIVE', totalSold: 234, viewCount: 1200,
  options: [
    { id: 1, title: 'Пицца 33 см + напиток', regularPrice: 89000, couponPrice: 45000, quantityLimit: 100, quantitySold: 34, status: 'ACTIVE' },
    { id: 2, title: 'Пицца 33 см + 2 напитка + десерт', regularPrice: 140000, couponPrice: 75000, quantityLimit: 50, quantitySold: 12, status: 'ACTIVE' },
  ],
  images: [], createdAt: '2026-03-01'
};

export default function CouponDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { addToCart, openCart } = useCartStore();

  const { data: coupon } = useQuery({
    queryKey: ['coupon', id],
    queryFn: () => couponsApi.getById(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const c = coupon || DEMO_COUPON;
  const buyDaysLeft = daysUntil(c.buyUntil);
  const discount = c.discountPercent || (c.oldPrice ? Math.round((1 - c.fromPrice / c.oldPrice) * 100) : 0);

  const handleAddToCart = (option: CouponOption) => {
    addToCart({
      couponOfferId: c.id,
      couponOptionId: option.id,
      couponTitle: c.title,
      optionTitle: option.title,
      unitPrice: option.couponPrice,
      quantity: 1,
    }).then(() => openCart()).catch(() => {});
  };

  return (
    <div className="detail-page">
      {/* Back */}
      <div className="detail-topbar container">
        <Link to="/coupons" className="detail-back">
          <ArrowLeft size={20} /> Назад
        </Link>
      </div>

      {/* Hero */}
      <div className="detail-hero">
        {c.coverImageUrl ? (
          <img src={c.coverImageUrl} alt={c.title} className="detail-hero__img" />
        ) : (
          <div className="detail-hero__placeholder">💎</div>
        )}
        {discount > 0 && <span className="detail-hero__discount">-{discount}%</span>}
      </div>

      <div className="detail-content container">
        {/* Info */}
        <div className="detail-main">
          <div className="detail-merchant-badge">{c.merchant.name}</div>
          <h1 className="detail-title">{c.title}</h1>
          {c.shortDescription && <p className="detail-short-desc">{c.shortDescription}</p>}

          <div className="detail-stats">
            <span><TrendingUp size={16} /> {c.totalSold} продано</span>
            <span><Star size={16} /> {c.viewCount} просмотров</span>
            {c.giftAvailable && <span className="detail-stat--gift"><Gift size={16} /> Подарок</span>}
          </div>

          {/* Options */}
          <div className="detail-options">
            <h2 className="detail-section-title">Выберите вариант</h2>
            {c.options.map((opt) => (
              <div key={opt.id} className="detail-option">
                <div className="detail-option__info">
                  <h3>{opt.title}</h3>
                  <div className="detail-option__pricing">
                    <span className="detail-option__old">{formatPrice(opt.regularPrice)}</span>
                    <span className="detail-option__price">{formatPrice(opt.couponPrice)}</span>
                  </div>
                  {opt.quantityLimit && (
                    <div className="detail-option__stock">
                      Осталось: {opt.quantityLimit - opt.quantitySold} шт.
                    </div>
                  )}
                </div>
                <button className="detail-option__btn" onClick={() => handleAddToCart(opt)}>
                  <ShoppingCart size={16} /> В корзину
                </button>
              </div>
            ))}
          </div>

          {/* Description */}
          {c.fullDescription && (
            <div className="detail-block">
              <h2 className="detail-section-title">Описание</h2>
              <p>{c.fullDescription}</p>
            </div>
          )}

          {c.terms && (
            <div className="detail-block">
              <h2 className="detail-section-title">Условия</h2>
              <p>{c.terms}</p>
            </div>
          )}

          {c.howToUse && (
            <div className="detail-block">
              <h2 className="detail-section-title">Как использовать</h2>
              <p style={{ whiteSpace: 'pre-line' }}>{c.howToUse}</p>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <aside className="detail-sidebar">
          <div className="detail-sidebar__card glass">
            <div className="detail-sidebar__pricing">
              {c.oldPrice && <span className="detail-sidebar__old">{formatPrice(c.oldPrice)}</span>}
              <span className="detail-sidebar__price">от {formatPrice(c.fromPrice)}</span>
            </div>

            <div className="detail-sidebar__dates">
              <div><Calendar size={16} /> Купить до: {formatDate(c.buyUntil)}</div>
              <div><Clock size={16} /> Использовать до: {formatDate(c.useUntil)}</div>
              {buyDaysLeft <= 7 && (
                <div className="detail-sidebar__urgent">
                  ⚡ {buyDaysLeft === 0 ? 'Последний день!' : `Осталось ${buyDaysLeft} дн.`}
                </div>
              )}
            </div>

            {c.address && (
              <div className="detail-sidebar__contact">
                <MapPin size={16} /> {c.address}
              </div>
            )}
            {c.contactPhone && (
              <div className="detail-sidebar__contact">
                <Phone size={16} /> {c.contactPhone}
              </div>
            )}
            {c.workingHours && (
              <div className="detail-sidebar__contact">
                <Clock size={16} /> {c.workingHours}
              </div>
            )}
          </div>
        </aside>
      </div>
    </div>
  );
}
