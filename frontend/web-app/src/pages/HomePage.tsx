import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowRight, Sparkles, TrendingUp, Zap, ChevronRight, Map } from 'lucide-react';
import { couponsApi } from '../api/coupons';
import type { Category, CouponOffer } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import './HomePage.css';

// Demo data for when backend is not running
const DEMO_CATEGORIES: Category[] = [
  { id: 1, name: 'Еда и напитки', slug: 'food', sortOrder: 1, iconUrl: '🍕' },
  { id: 2, name: 'Beauty', slug: 'beauty', sortOrder: 2, iconUrl: '💄' },
  { id: 3, name: 'Развлечения', slug: 'entertainment', sortOrder: 3, iconUrl: '🎮' },
  { id: 4, name: 'Здоровье и спорт', slug: 'health-sport', sortOrder: 4, iconUrl: '💪' },
  { id: 5, name: 'Услуги', slug: 'services', sortOrder: 5, iconUrl: '🔧' },
  { id: 6, name: 'Сертификаты', slug: 'gifts', sortOrder: 6, iconUrl: '🎁' },
];

const DEMO_COUPONS: CouponOffer[] = [
  {
    id: 1, title: 'Скидка на пиццу в PizzaLab', shortDescription: 'Любая пицца 33 см + напиток',
    merchant: { id: 1, name: 'PizzaLab' }, category: { id: 1, name: 'Еда', slug: 'food' },
    oldPrice: 89000, fromPrice: 45000, discountPercent: 49, coverImageUrl: '', buyUntil: '2026-04-30T00:00:00',
    useUntil: '2026-05-30T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 234, viewCount: 1200,
    options: [], images: [], createdAt: '2026-03-01'
  },
  {
    id: 2, title: 'SPA день для двоих', shortDescription: 'Хаммам + массаж + чай',
    merchant: { id: 2, name: 'Royal SPA' }, category: { id: 2, name: 'Beauty', slug: 'beauty' },
    oldPrice: 300000, fromPrice: 149000, discountPercent: 50, coverImageUrl: '', buyUntil: '2026-04-15T00:00:00',
    useUntil: '2026-05-15T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 89, viewCount: 560,
    options: [], images: [], createdAt: '2026-03-10'
  },
  {
    id: 3, title: 'Картинг — 15 минут', shortDescription: 'Заезд на гоночной трассе + шлем',
    merchant: { id: 3, name: 'Tashkent Karting' }, category: { id: 3, name: 'Развлечения', slug: 'entertainment' },
    oldPrice: 120000, fromPrice: 69000, discountPercent: 42, coverImageUrl: '', buyUntil: '2026-04-20T00:00:00',
    useUntil: '2026-06-01T00:00:00', giftAvailable: false, status: 'ACTIVE', totalSold: 456, viewCount: 2300,
    options: [], images: [], createdAt: '2026-03-05'
  },
  {
    id: 4, title: 'Абонемент в фитнес-клуб', shortDescription: '1 месяц безлимит + бассейн',
    merchant: { id: 4, name: 'FitLife' }, category: { id: 4, name: 'Здоровье и спорт', slug: 'health-sport' },
    oldPrice: 500000, fromPrice: 249000, discountPercent: 50, coverImageUrl: '', buyUntil: '2026-04-25T00:00:00',
    useUntil: '2026-07-01T00:00:00', giftAvailable: false, status: 'ACTIVE', totalSold: 178, viewCount: 890,
    options: [], images: [], createdAt: '2026-03-12'
  },
  {
    id: 5, title: 'Чистка лица ультразвук', shortDescription: 'УЗ чистка + маска + крем',
    merchant: { id: 5, name: 'Glow Clinic' }, category: { id: 2, name: 'Beauty', slug: 'beauty' },
    oldPrice: 200000, fromPrice: 99000, discountPercent: 51, coverImageUrl: '', buyUntil: '2026-04-18T00:00:00',
    useUntil: '2026-05-30T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 312, viewCount: 1500,
    options: [], images: [], createdAt: '2026-03-08'
  },
  {
    id: 6, title: 'Фотосессия — 1 час', shortDescription: 'Студия + обработка 10 фото',
    merchant: { id: 6, name: 'ArtPhoto Studio' }, category: { id: 5, name: 'Услуги', slug: 'services' },
    oldPrice: 350000, fromPrice: 179000, discountPercent: 49, coverImageUrl: '', buyUntil: '2026-04-22T00:00:00',
    useUntil: '2026-06-15T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 67, viewCount: 420,
    options: [], images: [], createdAt: '2026-03-15'
  },
];

export default function HomePage() {
  const [activeCategory, setActiveCategory] = useState<number | null>(null);

  const { data: categoriesData } = useQuery({
    queryKey: ['categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
    placeholderData: undefined,
  });

  const { data: couponsData } = useQuery({
    queryKey: ['coupons', activeCategory],
    queryFn: () => couponsApi.getCatalog({ categoryId: activeCategory ?? undefined, size: 12 }),
    select: (res) => res.data.data.content,
    placeholderData: undefined,
  });

  const categories = categoriesData || DEMO_CATEGORIES;
  const coupons = couponsData || DEMO_COUPONS;

  return (
    <div className="home-page">
      {/* Hero Section */}
      <section className="hero">
        <div className="hero-bg"></div>
        <div className="hero-content container">
          <div className="hero-badge">
            <Sparkles size={16} />
            Скидки до 70% в Ташкенте
          </div>
          <h1 className="hero-title">
            Купоны и скидки<br />
            <span className="text-gradient">в одном приложении</span>
          </h1>
          <p className="hero-subtitle">
            Лучшие предложения от ресторанов, салонов красоты, спорт-клубов, развлечений и многого другого
          </p>
          <div className="hero-actions">
            <Link to="/coupons" className="hero-btn hero-btn--primary">
              <Zap size={18} />
              Смотреть купоны
            </Link>
            <Link to="/bazaar" className="hero-btn hero-btn--secondary">
              <Map size={18} />
              Онлайн базар
            </Link>
          </div>
          <div className="hero-stats">
            <div className="hero-stat">
              <span className="hero-stat__number">500+</span>
              <span className="hero-stat__label">Купонов</span>
            </div>
            <div className="hero-stat">
              <span className="hero-stat__number">100+</span>
              <span className="hero-stat__label">Партнёров</span>
            </div>
            <div className="hero-stat">
              <span className="hero-stat__number">10K+</span>
              <span className="hero-stat__label">Покупок</span>
            </div>
          </div>
        </div>
      </section>

      {/* Categories */}
      <section className="section container">
        <h2 className="section-title">Категории</h2>
        <div className="categories-grid">
          <button
            className={`category-chip ${activeCategory === null ? 'category-chip--active' : ''}`}
            onClick={() => setActiveCategory(null)}
          >
            🔥 Все
          </button>
          {categories.map((cat) => (
            <button
              key={cat.id}
              className={`category-chip ${activeCategory === cat.id ? 'category-chip--active' : ''}`}
              onClick={() => setActiveCategory(cat.id)}
            >
              {cat.iconUrl || '📁'} {cat.name}
            </button>
          ))}
        </div>
      </section>

      {/* Top Selling */}
      <section className="section container">
        <div className="section-header">
          <h2 className="section-title">
            <TrendingUp size={24} className="section-icon" />
            Популярные купоны
          </h2>
          <Link to="/coupons" className="section-link">
            Все купоны <ChevronRight size={16} />
          </Link>
        </div>
        <div className="coupon-grid">
          {coupons.map((coupon) => (
            <CouponCard key={coupon.id} coupon={coupon} />
          ))}
        </div>
      </section>

      {/* Bazaar CTA */}
      <section className="section container">
        <div className="bazaar-cta">
          <div className="bazaar-cta__content">
            <span className="bazaar-cta__badge">🗺️ Новое</span>
            <h2 className="bazaar-cta__title">Онлайн Базар</h2>
            <p className="bazaar-cta__desc">
              Explore bazaars, ТЦ и рынки Ташкента на интерактивной карте. 
              Найдите магазины, узнайте цены и получите купоны со скидкой.
            </p>
            <Link to="/bazaar" className="bazaar-cta__btn">
              <Map size={18} />
              Открыть карту
              <ArrowRight size={18} />
            </Link>
          </div>
          <div className="bazaar-cta__visual">
            🏪
          </div>
        </div>
      </section>
    </div>
  );
}
