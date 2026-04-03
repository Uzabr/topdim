import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, SlidersHorizontal, X } from 'lucide-react';
import { couponsApi } from '../api/coupons';
import type { Category, CouponOffer } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import './CouponCatalogPage.css';

const DEMO_CATEGORIES: Category[] = [
  { id: 1, name: 'Еда и напитки', slug: 'food', sortOrder: 1, iconUrl: '🍕' },
  { id: 2, name: 'Beauty', slug: 'beauty', sortOrder: 2, iconUrl: '💄' },
  { id: 3, name: 'Развлечения', slug: 'entertainment', sortOrder: 3, iconUrl: '🎮' },
  { id: 4, name: 'Здоровье и спорт', slug: 'health-sport', sortOrder: 4, iconUrl: '💪' },
  { id: 5, name: 'Услуги', slug: 'services', sortOrder: 5, iconUrl: '🔧' },
  { id: 6, name: 'Сертификаты', slug: 'gifts', sortOrder: 6, iconUrl: '🎁' },
];

const DEMO_COUPONS: CouponOffer[] = [
  { id: 1, title: 'Скидка на пиццу в PizzaLab', shortDescription: 'Любая пицца 33 см + напиток', merchant: { id: 1, name: 'PizzaLab' }, category: { id: 1, name: 'Еда', slug: 'food' }, oldPrice: 89000, fromPrice: 45000, discountPercent: 49, coverImageUrl: '', buyUntil: '2026-04-30T00:00:00', useUntil: '2026-05-30T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 234, viewCount: 1200, options: [], images: [], createdAt: '2026-03-01' },
  { id: 2, title: 'SPA день для двоих', shortDescription: 'Хаммам + массаж + чай', merchant: { id: 2, name: 'Royal SPA' }, category: { id: 2, name: 'Beauty', slug: 'beauty' }, oldPrice: 300000, fromPrice: 149000, discountPercent: 50, coverImageUrl: '', buyUntil: '2026-04-15T00:00:00', useUntil: '2026-05-15T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 89, viewCount: 560, options: [], images: [], createdAt: '2026-03-10' },
  { id: 3, title: 'Картинг — 15 минут', shortDescription: 'Заезд на гоночной трассе + шлем', merchant: { id: 3, name: 'Tashkent Karting' }, category: { id: 3, name: 'Развлечения', slug: 'entertainment' }, oldPrice: 120000, fromPrice: 69000, discountPercent: 42, coverImageUrl: '', buyUntil: '2026-04-20T00:00:00', useUntil: '2026-06-01T00:00:00', giftAvailable: false, status: 'ACTIVE', totalSold: 456, viewCount: 2300, options: [], images: [], createdAt: '2026-03-05' },
  { id: 4, title: 'Абонемент в фитнес-клуб', shortDescription: '1 месяц безлимит + бассейн', merchant: { id: 4, name: 'FitLife' }, category: { id: 4, name: 'Здоровье', slug: 'health-sport' }, oldPrice: 500000, fromPrice: 249000, discountPercent: 50, coverImageUrl: '', buyUntil: '2026-04-25T00:00:00', useUntil: '2026-07-01T00:00:00', giftAvailable: false, status: 'ACTIVE', totalSold: 178, viewCount: 890, options: [], images: [], createdAt: '2026-03-12' },
  { id: 5, title: 'Чистка лица ультразвук', shortDescription: 'УЗ чистка + маска + крем', merchant: { id: 5, name: 'Glow Clinic' }, category: { id: 2, name: 'Beauty', slug: 'beauty' }, oldPrice: 200000, fromPrice: 99000, discountPercent: 51, coverImageUrl: '', buyUntil: '2026-04-18T00:00:00', useUntil: '2026-05-30T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 312, viewCount: 1500, options: [], images: [], createdAt: '2026-03-08' },
  { id: 6, title: 'Фотосессия — 1 час', shortDescription: 'Студия + обработка 10 фото', merchant: { id: 6, name: 'ArtPhoto Studio' }, category: { id: 5, name: 'Услуги', slug: 'services' }, oldPrice: 350000, fromPrice: 179000, discountPercent: 49, coverImageUrl: '', buyUntil: '2026-04-22T00:00:00', useUntil: '2026-06-15T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 67, viewCount: 420, options: [], images: [], createdAt: '2026-03-15' },
];

const SORT_OPTIONS = [
  { value: 'popular', label: 'Популярные' },
  { value: 'new', label: 'Новые' },
  { value: 'price_asc', label: 'Сначала дешёвые' },
  { value: 'price_desc', label: 'Сначала дорогие' },
  { value: 'discount', label: 'Скидки' },
];

export default function CouponCatalogPage() {
  const [search, setSearch] = useState('');
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [sortBy, setSortBy] = useState('popular');
  const [page, setPage] = useState(0);

  const { data: categoriesData } = useQuery({
    queryKey: ['categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
  });

  const { data: couponsData, isLoading } = useQuery({
    queryKey: ['coupons-catalog', activeCategory, search, sortBy, page],
    queryFn: () => couponsApi.getCatalog({
      categoryId: activeCategory ?? undefined,
      search: search || undefined,
      sortBy,
      page,
      size: 20,
    }),
    select: (res) => res.data.data,
  });

  const categories = categoriesData && categoriesData.length > 0 ? categoriesData : DEMO_CATEGORIES;
  
  // If we have an actual response from the backend (even empty), use it.
  const coupons = couponsData ? couponsData.content : DEMO_COUPONS;
  const totalPages = couponsData?.totalPages || 1;

  return (
    <div className="catalog-page">
      <div className="catalog-header container">
        <h1 className="catalog-title">Каталог купонов</h1>
        <p className="catalog-subtitle">Найдите лучшие скидки в Ташкенте</p>
      </div>

      {/* Search & Sort */}
      <div className="catalog-controls container">
        <div className="catalog-search">
          <Search size={18} />
          <input
            type="text"
            placeholder="Поиск купонов..."
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0); }}
          />
          {search && (
            <button onClick={() => setSearch('')} className="catalog-search__clear">
              <X size={16} />
            </button>
          )}
        </div>
        <div className="catalog-sort">
          <SlidersHorizontal size={16} />
          <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
            {SORT_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Categories */}
      <div className="catalog-categories container">
        <button
          className={`category-chip ${activeCategory === null ? 'category-chip--active' : ''}`}
          onClick={() => { setActiveCategory(null); setPage(0); }}
        >
          🔥 Все
        </button>
        {categories.map((cat) => (
          <button
            key={cat.id}
            className={`category-chip ${activeCategory === cat.id ? 'category-chip--active' : ''}`}
            onClick={() => { setActiveCategory(cat.id); setPage(0); }}
          >
            {cat.iconUrl || '📁'} {cat.name}
          </button>
        ))}
      </div>

      {/* Results */}
      <div className="catalog-results container">
        {isLoading ? (
          <div className="catalog-loading">
            {[...Array(6)].map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 280, borderRadius: 16 }} />
            ))}
          </div>
        ) : coupons.length === 0 ? (
          <div className="catalog-empty">
            <span className="catalog-empty__icon">🔍</span>
            <h3>Купоны не найдены</h3>
            <p>Попробуйте изменить фильтры</p>
          </div>
        ) : (
          <div className="coupon-grid">
            {coupons.map((coupon) => (
              <CouponCard key={coupon.id} coupon={coupon} />
            ))}
          </div>
        )}

        {/* Pagination */}
        {totalPages > 1 && (
          <div className="catalog-pagination">
            <button
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
              className="pagination-btn"
            >
              ← Назад
            </button>
            <span className="pagination-info">
              {page + 1} / {totalPages}
            </span>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
              className="pagination-btn"
            >
              Далее →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
