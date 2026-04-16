import { useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Heart, Gift, ShoppingCart, TrendingUp, Calendar, Clock, AlertCircle, Info, Users, CreditCard, Check } from 'lucide-react';
import ReactMarkdown from 'react-markdown';
import TwoGisMap from '../components/map/TwoGisMap';
import { couponsApi } from '../api/coupons';
import type { CouponOffer, CouponOption } from '../api/coupons';
import { useCartStore } from '../store/cartStore';
import { useAuthStore } from '../store/authStore';
import { useFavoritesStore } from '../store/favoritesStore';
import { formatPrice, daysUntil, formatDate } from '../utils/format';
import Breadcrumbs from '../components/ui/Breadcrumbs';
import ImageSlider from '../components/ui/ImageSlider';
import Tabs from '../components/ui/Tabs';
import StarRating from '../components/ui/StarRating';
import RevealPhone from '../components/ui/RevealPhone';
import ShareButton from '../components/ui/ShareButton';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { useLocalePath } from '../hooks/useLocalePath';
import { topdimDeals } from '../data/topdim';
import './CouponDetailPage.css';

const DEMO_COUPON: CouponOffer = {
  id: 1, title: 'Скидка на пиццу в PizzaLab', shortDescription: 'Любая пицца 33 см + напиток',
  fullDescription: 'Отличное предложение от PizzaLab! Выберите любую пиццу диаметром 33 см из нашего меню и получите напиток на выбор совершенно бесплатно.\n\nПредложение действует во всех филиалах PizzaLab в Ташкенте.',
  merchant: { id: 1, name: 'PizzaLab', logoUrl: '' },
  category: { id: 1, name: 'Еда', slug: 'food' },
  oldPrice: 89000, fromPrice: 45000, discountPercent: 49,
  coverImageUrl: 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1200&q=80',
  buyUntil: '2026-04-30T00:00:00', useUntil: '2026-05-30T00:00:00',
  terms: '- 1 купон на 1 человека в день\n- Действует в будние дни\n- Необходимо бронирование',
  usageRules: '- Показать купон официанту перед заказом\n- Назвать номер купона при бронировании',
  howToUse: '1. Покажите купон\n2. Выберите пиццу\n3. Наслаждайтесь',
  address: 'Ташкент, Мирзо Улугбека, 55', contactPhone: '+998 90 123 45 67',
  workingHours: '10:00 – 22:00', giftAvailable: true, status: 'ACTIVE', totalSold: 1234, viewCount: 5600,
  options: [
    { id: 1, title: 'Пицца 33 см + напиток', regularPrice: 89000, couponPrice: 45000, quantityLimit: 100, quantitySold: 834, status: 'ACTIVE' },
    { id: 2, title: 'Пицца 33 см + 2 напитка + десерт', regularPrice: 140000, couponPrice: 75000, quantityLimit: 50, quantitySold: 312, status: 'ACTIVE' },
  ],
  images: [
    'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?auto=format&fit=crop&w=1200&q=80',
    'https://images.unsplash.com/photo-1574071318508-1cdbab80d002?auto=format&fit=crop&w=1200&q=80',
  ],
  createdAt: '2026-03-01'
};

const MOCK_REVIEWS = [
  { id: 1, author: 'Алишер М.', rating: 5, text: 'Отличная пицца! Быстро обслужили, всем рекомендую.', date: '2026-03-28' },
  { id: 2, author: 'Гулнора Т.', rating: 4, text: 'Хорошее предложение, но ждали 30 минут. В целом вкусно.', date: '2026-03-15' },
  { id: 3, author: 'Дмитрий К.', rating: 5, text: 'Уже третий раз покупаем купон, каждый раз всё отлично!', date: '2026-03-10' },
];

const BASE_TABS = [
  { key: 'info', label: 'Информация' },
  { key: 'reviews', label: 'Отзывы', badge: MOCK_REVIEWS.length },
];

export default function CouponDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { addToCart } = useCartStore();
  const { isAuthenticated } = useAuthStore();
  const { toggleFavorite, isFavorite } = useFavoritesStore();
  const [activeTab, setActiveTab] = useState('info');
  const [toastMessage, setToastMessage] = useState('');
  const optionsRef = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const lp = useLocalePath();

  const { data: coupon } = useQuery({
    queryKey: ['coupon', id],
    queryFn: () => couponsApi.getById(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const c = coupon || DEMO_COUPON;
  const buyDaysLeft = daysUntil(c.buyUntil);
  const discount = c.discountPercent || (c.oldPrice ? Math.round((1 - c.fromPrice / c.oldPrice) * 100) : 0);
  const fav = isFavorite(c.id);
  const images = c.images?.length > 0 ? c.images : (c.coverImageUrl ? [c.coverImageUrl] : []);

  const detailTabs = [
    ...BASE_TABS,
    ...(isAuthenticated ? [{ key: 'contacts', label: 'Контакты' }] : []),
  ];

  const showToast = (msg: string) => {
    setToastMessage(msg);
    setTimeout(() => setToastMessage(''), 3000);
  };

  const handleAddToCart = (option: CouponOption) => {
    addToCart({
      couponOfferId: c.id,
      couponOptionId: option.id,
      couponTitle: c.title,
      optionTitle: option.title,
      unitPrice: option.couponPrice,
      quantity: 1,
      coverImageUrl: c.coverImageUrl,
    });
    showToast(`«${option.title}» добавлен в корзину!`);
  };

  const handleBuyNow = (option: CouponOption) => {
    addToCart({
      couponOfferId: c.id,
      couponOptionId: option.id,
      couponTitle: c.title,
      optionTitle: option.title,
      unitPrice: option.couponPrice,
      quantity: 1,
      coverImageUrl: c.coverImageUrl,
    });
    navigate(lp('/checkout'));
  };

  const scrollToOptions = () => {
    optionsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  // Related deals
  const relatedDeals: CouponCardData[] = topdimDeals
    .filter((d) => d.id !== c.id)
    .slice(0, 6)
    .map((d) => ({
      id: d.id,
      title: d.title,
      shortDescription: d.shortDescription,
      merchant: d.merchant,
      category: d.category,
      oldPrice: d.oldPrice,
      fromPrice: d.fromPrice,
      discountPercent: d.discountPercent,
      coverImageUrl: d.coverImageUrl || d.image,
      totalSold: d.totalSold,
      rating: d.rating,
      reviewCount: d.reviews,
      location: d.location,
      isHot: d.isHot,
    }));

  // Average rating
  const avgRating = MOCK_REVIEWS.reduce((s, r) => s + r.rating, 0) / MOCK_REVIEWS.length;

  return (
    <div className="detail-page">
      {/* SEO: title */}
      {typeof document !== 'undefined' && (document.title = `${c.title} | TopDim`)}

      {/* Breadcrumbs */}
      <div className="container">
        <Breadcrumbs items={[
          { label: c.category?.name || 'Каталог', to: `/coupons${c.category ? `?category=${c.category.slug}` : ''}` },
          { label: c.title },
        ]} />
      </div>

      {/* Image Gallery */}
      <div className="detail-gallery container">
        <ImageSlider images={images} alt={c.title} fallbackText={c.merchant?.name || 'TopDim'} />
      </div>

      <div className="detail-content container">
        {/* Main */}
        <div className="detail-main">
          {/* Title block */}
          <div className="detail-title-block">
            <div className="detail-title-row">
              <div>
                <div className="detail-merchant-badge">{c.merchant?.name}</div>
                <h1 className="detail-title">{c.title}</h1>
                {c.shortDescription && <p className="detail-short-desc">{c.shortDescription}</p>}
              </div>
              <div className="detail-actions">
                <button
                  className={`detail-fav-btn ${fav ? 'detail-fav-btn--active' : ''}`}
                  onClick={() => toggleFavorite(c.id)}
                  aria-label="В избранное"
                >
                  <Heart size={20} fill={fav ? 'currentColor' : 'none'} />
                </button>
                <ShareButton title={c.title} variant="icon" />
              </div>
            </div>

            <div className="detail-stats">
              <StarRating rating={avgRating} reviewCount={MOCK_REVIEWS.length} size={16} />
              <span className="detail-stat">
                <TrendingUp size={15} />
                {c.totalSold.toLocaleString('ru-RU')} покупок
              </span>
              {c.giftAvailable && (
                <span className="detail-stat detail-stat--gift">
                  <Gift size={15} />
                  Подарок
                </span>
              )}
            </div>
          </div>

          {/* Tabs */}
          <Tabs tabs={detailTabs} activeKey={activeTab} onChange={setActiveTab} sticky />

          {/* Tab content */}
          <div className="detail-tab-content">
            {/* ═══ INFO TAB ═══ */}
            {activeTab === 'info' && (
              <div className="detail-info-tab">
                {c.terms && (
                  <div className="detail-block detail-block--warning">
                    <h2 className="detail-section-title">
                      <AlertCircle size={18} />
                      Важная информация
                    </h2>
                    <div className="markdown-body">
                      <ReactMarkdown>{c.terms}</ReactMarkdown>
                    </div>
                  </div>
                )}

                {c.usageRules && (
                  <div className="detail-block">
                    <h2 className="detail-section-title">Правила использования</h2>
                    <div className="markdown-body">
                      <ReactMarkdown>{c.usageRules}</ReactMarkdown>
                    </div>
                  </div>
                )}

                {c.howToUse && (
                  <div className="detail-block">
                    <h2 className="detail-section-title">Как использовать</h2>
                    <div className="markdown-body">
                      <ReactMarkdown>{c.howToUse}</ReactMarkdown>
                    </div>
                  </div>
                )}

                {c.fullDescription && (
                  <div className="detail-block">
                    <h2 className="detail-section-title">
                      <Info size={18} />
                      О заведении
                    </h2>
                    <div className="markdown-body">
                      <ReactMarkdown>{c.fullDescription}</ReactMarkdown>
                    </div>
                  </div>
                )}
              </div>
            )}

            {/* ═══ REVIEWS TAB ═══ */}
            {activeTab === 'reviews' && (
              <div className="detail-reviews-tab">
                <div className="reviews-summary">
                  <div className="reviews-summary__big">
                    <span className="reviews-summary__number">{avgRating.toFixed(1)}</span>
                    <StarRating rating={avgRating} showCount={false} size={20} />
                    <span className="reviews-summary__total">{MOCK_REVIEWS.length} отзывов</span>
                  </div>
                  <div className="reviews-summary__bars">
                    {[5, 4, 3, 2, 1].map((star) => {
                      const count = MOCK_REVIEWS.filter((r) => r.rating === star).length;
                      const pct = (count / MOCK_REVIEWS.length) * 100;
                      return (
                        <div key={star} className="reviews-bar">
                          <span>{star}★</span>
                          <div className="reviews-bar__track">
                            <div className="reviews-bar__fill" style={{ width: `${pct}%` }} />
                          </div>
                          <span className="reviews-bar__count">{count}</span>
                        </div>
                      );
                    })}
                  </div>
                </div>

                <div className="reviews-list">
                  {MOCK_REVIEWS.map((review) => (
                    <div key={review.id} className="review-card">
                      <div className="review-card__header">
                        <div className="review-card__avatar">
                          {review.author.charAt(0)}
                        </div>
                        <div>
                          <div className="review-card__author">{review.author}</div>
                          <StarRating rating={review.rating} showCount={false} size={13} />
                        </div>
                        <span className="review-card__date">{formatDate(review.date)}</span>
                      </div>
                      <p className="review-card__text">{review.text}</p>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* ═══ CONTACTS TAB ═══ */}
            {activeTab === 'contacts' && (
              <div className="detail-contacts-tab">
                {c.address && (
                  <div className="detail-map-section">
                    <div className="detail-map-container" style={{ height: '260px', width: '100%', borderRadius: '16px', overflow: 'hidden', position: 'relative' }}>
                      <TwoGisMap 
                        center={[69.2401, 41.2995]} 
                        zoom={15}
                        staticMarker={{ lat: 41.2995, lon: 69.2401, title: c.merchant?.name }}
                      />
                    </div>
                    <div className="detail-address">
                      <strong>📍 Адрес</strong>
                      <p>{c.address}</p>
                    </div>
                  </div>
                )}

                {c.contactPhone && (
                  <RevealPhone phone={c.contactPhone} />
                )}

                {c.workingHours && (
                  <div className="detail-contact-item">
                    <Clock size={16} />
                    <div>
                      <span className="detail-contact-label">Часы работы</span>
                      <p>{c.workingHours}</p>
                    </div>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Options */}
          <div className="detail-options" ref={optionsRef}>
            <h2 className="detail-section-title">Выберите сертификат</h2>
            {(() => {
              // Если нет вариантов, создаём fallback из базовых цен купона
              const displayOptions: CouponOption[] = c.options && c.options.length > 0
                ? c.options
                : [{
                    id: 0,
                    title: c.title,
                    regularPrice: c.oldPrice || c.fromPrice,
                    couponPrice: c.fromPrice,
                    quantityLimit: 0,
                    quantitySold: c.totalSold || 0,
                    status: 'ACTIVE',
                  }];

              return displayOptions.map((opt) => {
                const remaining = opt.quantityLimit ? opt.quantityLimit - opt.quantitySold : null;
                const soldPct = opt.quantityLimit ? (opt.quantitySold / opt.quantityLimit) * 100 : 0;

                return (
                  <div key={opt.id} className="detail-option">
                    <div className="detail-option__info">
                      <h3>{opt.title}</h3>
                      <span className="detail-option__bought">
                        <Users size={14} />
                        Купили {opt.quantitySold.toLocaleString('ru-RU')} человек
                      </span>
                      <div className="detail-option__pricing">
                        <span className="detail-option__price">{formatPrice(opt.couponPrice)}</span>
                        {opt.regularPrice !== opt.couponPrice && (
                          <span className="detail-option__old">{formatPrice(opt.regularPrice)}</span>
                        )}
                      </div>
                      {remaining !== null && (
                        <div className="detail-option__stock">
                          <div className="detail-option__progress">
                            <div className="detail-option__progress-fill" style={{ width: `${soldPct}%` }} />
                          </div>
                          <span>Осталось {remaining} шт.</span>
                        </div>
                      )}
                    </div>
                    <div className="detail-option__buttons">
                      <button className="detail-option__btn detail-option__btn--buy" onClick={() => handleBuyNow(opt)}>
                        <CreditCard size={15} />
                        Купить
                      </button>
                      <button className="detail-option__btn detail-option__btn--cart" onClick={() => handleAddToCart(opt)}>
                        <ShoppingCart size={15} />
                        В корзину
                      </button>
                    </div>
                  </div>
                );
              });
            })()}
          </div>

          {/* Related deals */}
          {relatedDeals.length > 0 && (
            <div className="detail-related">
              <h2 className="detail-section-title">Похожие акции</h2>
              <div className="detail-related__carousel">
                {relatedDeals.map((deal) => (
                  <CouponCard key={deal.id} coupon={deal} layout="carousel" />
                ))}
              </div>
            </div>
          )}
        </div>

        {/* Sidebar */}
        <aside className="detail-sidebar">
          <div className="detail-sidebar__card glass">
            <div className="detail-sidebar__pricing">
              {c.oldPrice && <span className="detail-sidebar__old">{formatPrice(c.oldPrice)}</span>}
              <span className="detail-sidebar__price">от {formatPrice(c.fromPrice)}</span>
              {discount > 0 && <span className="detail-sidebar__discount-badge">-{discount}%</span>}
            </div>

            <div className="detail-sidebar__dates">
              <div><Calendar size={15} /> Купить до: {formatDate(c.buyUntil)}</div>
              <div><Clock size={15} /> Использовать до: {formatDate(c.useUntil)}</div>
              {buyDaysLeft <= 7 && (
                <div className="detail-sidebar__urgent">
                  ⚡ {buyDaysLeft === 0 ? 'Последний день!' : `Осталось ${buyDaysLeft} дн.`}
                </div>
              )}
            </div>

            <button className="detail-sidebar__cta primary-button" onClick={scrollToOptions}>
              Выбрать сертификат
            </button>
          </div>
        </aside>
      </div>

      {/* Sticky CTA (mobile) */}
      <button className="detail-sticky-cta" onClick={scrollToOptions}>
        Выбрать сертификат
      </button>

      {/* Toast notification */}
      {toastMessage && (
        <div className="detail-toast">
          <Check size={18} />
          {toastMessage}
        </div>
      )}
    </div>
  );
}
