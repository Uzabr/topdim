import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, SlidersHorizontal, X, LayoutGrid, List, Sparkles } from 'lucide-react';
import { couponsApi } from '../api/coupons';
import type { Category, CouponOffer } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import './CouponCatalogPage.css';

const DEMO_CATEGORIES: Category[] = [
  { id: 1, name: 'Еда и напитки', slug: 'food', sortOrder: 1, iconUrl: '🍕' },
  { id: 2, name: 'Beauty', slug: 'beauty', sortOrder: 2, iconUrl: '💄' },
  { id: 3, name: 'Развлечения', slug: 'entertainment', sortOrder: 3, iconUrl: '🎮' },
  { id: 4, name: 'Здоровье и спорт', slug: 'health-sport', sortOrder: 4, iconUrl: '💪' },
  { id: 5, name: 'Услуги', slug: 'services', sortOrder: 5, iconUrl: '🔧' },
  { id: 6, name: 'Сертификаты', slug: 'gifts', sortOrder: 6, iconUrl: '🎁' },
];

export const DEMO_COUPONS: CouponOffer[] = [
  { id: 1, title: 'Скидка на пиццу в PizzaLab', shortDescription: 'Любая пицца 33 см + напиток', merchant: { id: 1, name: 'PizzaLab' }, category: { id: 1, name: 'Еда', slug: 'food' }, oldPrice: 89000, fromPrice: 45000, discountPercent: 49, coverImageUrl: 'https://images.unsplash.com/photo-1504674900247-0877df9cc836?auto=format&fit=crop&w=600&q=80', buyUntil: '2026-04-30T00:00:00', useUntil: '2026-05-30T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 234, viewCount: 1200, options: [], images: [], createdAt: '2026-03-01', address: 'Мирзо Улугбека' },
  { id: 2, title: 'SPA день для двоих', shortDescription: 'Хаммам + массаж + чай', merchant: { id: 2, name: 'Royal SPA' }, category: { id: 2, name: 'Beauty', slug: 'beauty' }, oldPrice: 300000, fromPrice: 149000, discountPercent: 50, coverImageUrl: 'https://images.unsplash.com/photo-1515377905703-c4788e51af15?auto=format&fit=crop&w=600&q=80', buyUntil: '2026-04-15T00:00:00', useUntil: '2026-05-15T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 89, viewCount: 560, options: [], images: [], createdAt: '2026-03-10', address: 'Mirabad' },
  { id: 3, title: 'Картинг — 15 минут', shortDescription: 'Заезд на гоночной трассе + шлем', merchant: { id: 3, name: 'Tashkent Karting' }, category: { id: 3, name: 'Развлечения', slug: 'entertainment' }, oldPrice: 120000, fromPrice: 69000, discountPercent: 42, coverImageUrl: 'https://images.unsplash.com/photo-1511512578047-dfb367046420?auto=format&fit=crop&w=600&q=80', buyUntil: '2026-04-20T00:00:00', useUntil: '2026-06-01T00:00:00', giftAvailable: false, status: 'ACTIVE', totalSold: 456, viewCount: 2300, options: [], images: [], createdAt: '2026-03-05', address: 'Tashkent City' },
  { id: 4, title: 'Абонемент в фитнес-клуб', shortDescription: '1 месяц безлимит + бассейн', merchant: { id: 4, name: 'FitLife' }, category: { id: 4, name: 'Здоровье', slug: 'health-sport' }, oldPrice: 500000, fromPrice: 249000, discountPercent: 50, coverImageUrl: 'https://images.unsplash.com/photo-1517836357463-d25dfeac3438?auto=format&fit=crop&w=600&q=80', buyUntil: '2026-04-25T00:00:00', useUntil: '2026-07-01T00:00:00', giftAvailable: false, status: 'ACTIVE', totalSold: 178, viewCount: 890, options: [], images: [], createdAt: '2026-03-12', address: 'Юнусабад' },
  { id: 5, title: 'Чистка лица ультразвук', shortDescription: 'УЗ чистка + маска + крем', merchant: { id: 5, name: 'Glow Clinic' }, category: { id: 2, name: 'Beauty', slug: 'beauty' }, oldPrice: 200000, fromPrice: 99000, discountPercent: 51, coverImageUrl: 'https://images.unsplash.com/photo-1522335789203-aabd1fc54bc9?auto=format&fit=crop&w=600&q=80', buyUntil: '2026-04-18T00:00:00', useUntil: '2026-05-30T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 312, viewCount: 1500, options: [], images: [], createdAt: '2026-03-08', address: 'Чиланзар' },
  { id: 6, title: 'Фотосессия — 1 час', shortDescription: 'Студия + обработка 10 фото', merchant: { id: 6, name: 'ArtPhoto Studio' }, category: { id: 5, name: 'Услуги', slug: 'services' }, oldPrice: 350000, fromPrice: 179000, discountPercent: 49, coverImageUrl: 'https://images.unsplash.com/photo-1516035069371-29a1b244cc32?auto=format&fit=crop&w=600&q=80', buyUntil: '2026-04-22T00:00:00', useUntil: '2026-06-15T00:00:00', giftAvailable: true, status: 'ACTIVE', totalSold: 67, viewCount: 420, options: [], images: [], createdAt: '2026-03-15', address: 'Ц-1' },
];

const SORT_OPTIONS = [
  { value: 'popular', label: 'Популярные' },
  { value: 'new', label: 'Новые' },
  { value: 'price_asc', label: 'Сначала дешёвые' },
  { value: 'price_desc', label: 'Сначала дорогие' },
  { value: 'discount', label: 'По скидке' },
];

function mapToCardData(coupon: CouponOffer): CouponCardData {
  return {
    id: coupon.id,
    title: coupon.title,
    shortDescription: coupon.shortDescription,
    merchant: coupon.merchant,
    category: coupon.category,
    oldPrice: coupon.oldPrice,
    fromPrice: coupon.fromPrice,
    discountPercent: coupon.discountPercent,
    coverImageUrl: coupon.coverImageUrl,
    totalSold: coupon.totalSold,
    rating: (coupon as any).rating || 4.5 + Math.random() * 0.4,
    reviewCount: (coupon as any).reviewCount || Math.floor(coupon.totalSold * 0.3),
    address: coupon.address,
    location: coupon.address,
    giftAvailable: coupon.giftAvailable,
    isHot: (coupon.discountPercent || 0) >= 50,
  };
}

export default function CouponCatalogPage() {
  const [search, setSearch] = useState('');
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [sortBy, setSortBy] = useState('popular');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
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
  const coupons = couponsData ? couponsData.content : DEMO_COUPONS;
  const totalPages = couponsData?.totalPages || 1;
  const totalElements = couponsData?.totalElements || coupons.length;

  return (
    <div className="catalog-page">
      {/* Hero */}
      <div className="catalog-hero">
        <div className="catalog-hero__inner container">
          <div className="catalog-hero__eyebrow">
            <Sparkles size={16} />
            Все скидки города
          </div>
          <h1 className="catalog-hero__title">Каталог купонов</h1>
          <p className="catalog-hero__subtitle">
            Откройте для себя лучшие предложения от проверенных партнёров
          </p>
        </div>
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
        <div className="catalog-toolbar">
          <div className="catalog-sort">
            <SlidersHorizontal size={15} />
            <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
              {SORT_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
          </div>
          <div className="catalog-view-toggle">
            <button
              className={viewMode === 'grid' ? 'active' : ''}
              onClick={() => setViewMode('grid')}
              aria-label="Сетка"
            >
              <LayoutGrid size={16} />
            </button>
            <button
              className={viewMode === 'list' ? 'active' : ''}
              onClick={() => setViewMode('list')}
              aria-label="Список"
            >
              <List size={16} />
            </button>
          </div>
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

      {/* Result count */}
      <div className="catalog-meta container">
        <span className="catalog-meta__count">
          Найдено <strong>{totalElements}</strong> купонов
        </span>
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
          <div className={`coupon-grid ${viewMode === 'list' ? 'coupon-grid--list' : ''}`}>
            {coupons.map((coupon) => (
              <CouponCard key={coupon.id} coupon={mapToCardData(coupon)} layout="card" />
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
