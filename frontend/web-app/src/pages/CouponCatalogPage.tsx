import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Search, SlidersHorizontal, X, LayoutGrid, List, Sparkles, Coffee, Scissors, Dumbbell, Gamepad2, Plane, Baby } from 'lucide-react';
import { couponsApi } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import Select from '../components/ui/Select';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
import { localizedName } from '../utils/localizedText';
import './CouponCatalogPage.css';

const CategoryIcon = ({ slug }: { slug?: string }) => {
  switch (slug) {
    case 'food': return <Coffee size={16} />;
    case 'beauty': return <Scissors size={16} />;
    case 'sport': case 'health-sport': return <Dumbbell size={16} />;
    case 'entertainment': return <Gamepad2 size={16} />;
    case 'travel': return <Plane size={16} />;
    case 'kids': return <Baby size={16} />;
    default: return <Sparkles size={16} />;
  }
};

export default function CouponCatalogPage() {
  const { t, i18n } = useTranslation();
  const [search, setSearch] = useState('');

  const sortOptions = useMemo(
    () => [
      { id: 'popular', label: t('catalog.sortPopular') },
      { id: 'new', label: t('catalog.sortNew') },
      { id: 'price_asc', label: t('catalog.sortPriceAsc') },
      { id: 'price_desc', label: t('catalog.sortPriceDesc') },
      { id: 'discount', label: t('catalog.sortDiscount') },
    ],
    [t, i18n.language],
  );
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [sortBy, setSortBy] = useState('popular');
  const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
  const [page, setPage] = useState(0);

  const {
    data: categoriesData,
    isLoading: isCategoriesLoading,
    isError: isCategoriesError,
  } = useQuery({
    queryKey: ['categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
  });

  const {
    data: couponsData,
    isLoading,
    isError,
  } = useQuery({
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

  const categories = categoriesData ?? [];
  const coupons = couponsData?.content ?? [];
  const totalPages = couponsData?.totalPages ?? 1;
  const totalElements = couponsData?.totalElements ?? 0;

  return (
    <div className="catalog-page">
      {/* Hero */}
      <div className="catalog-hero">
        <div className="catalog-hero__inner container">
          <div className="catalog-hero__eyebrow">
            <Sparkles size={16} />
            {t('catalog.eyebrow')}
          </div>
          <h1 className="catalog-hero__title">{t('catalog.title')}</h1>
          <p className="catalog-hero__subtitle">
            {t('catalog.subtitle')}
          </p>
        </div>
      </div>

      {/* Search & Sort */}
      <div className="catalog-controls container">
        <div className="catalog-search">
          <Search size={18} />
          <input
            type="text"
            placeholder={t('catalog.searchPlaceholder')}
            value={search}
            onChange={(e) => {
              const val = e.target.value;
              setSearch(val);
              setPage(0);
              // Авто-выбор категории по названию
              if (val.trim()) {
                const match = categories.find((c) => c.name.toLowerCase().includes(val.trim().toLowerCase()));
                if (match) {
                  setActiveCategory(match.id);
                } else {
                  setActiveCategory(null);
                }
              } else {
                setActiveCategory(null);
              }
            }}
          />
          {search && (
            <button onClick={() => { setSearch(''); setActiveCategory(null); }} className="catalog-search__clear">
              <X size={16} />
            </button>
          )}
        </div>
        <div className="catalog-toolbar">
          <div className="catalog-sort">
            <Select
              options={sortOptions}
              value={sortBy}
              onChange={(val) => setSortBy(val as string)}
              triggerIcon={<SlidersHorizontal size={15} />}
              minWidth="190px"
              align="right"
            />
          </div>
          <div className="catalog-view-toggle">
            <button
              className={viewMode === 'grid' ? 'active' : ''}
              onClick={() => setViewMode('grid')}
              aria-label={t('catalog.viewGrid')}
            >
              <LayoutGrid size={16} />
            </button>
            <button
              className={viewMode === 'list' ? 'active' : ''}
              onClick={() => setViewMode('list')}
              aria-label={t('catalog.viewList')}
            >
              <List size={16} />
            </button>
          </div>
        </div>
      </div>

      {/* Categories */}
      <div className="catalog-categories container">
        <button
          className={`filter-chip ${activeCategory === null ? 'filter-chip--active' : ''}`}
          onClick={() => { setActiveCategory(null); setPage(0); }}
        >
          {t('common.all')}
        </button>
        {!isCategoriesLoading && !isCategoriesError && categories.map((cat) => (
          <button
            key={cat.id}
            className={`filter-chip ${activeCategory === cat.id ? 'filter-chip--active' : ''}`}
            onClick={() => { setActiveCategory(cat.id); setPage(0); }}
          >
            <CategoryIcon slug={cat.slug} /> {localizedName(cat, i18n.language)}
          </button>
        ))}
      </div>

      {/* Result count */}
      <div className="catalog-meta container">
        <span className="catalog-meta__count">
          {t('catalog.found', { count: totalElements })}
        </span>
      </div>

      {/* Results */}
      <div className="catalog-results container">
        {isLoading ? (
          <div className="catalog-loading">
            {Array.from({ length: 6 }).map((_, i) => (
              <div key={i} className="skeleton" style={{ height: 280, borderRadius: 16 }} />
            ))}
          </div>
        ) : isError ? (
          <div className="catalog-empty">
            <span className="catalog-empty__icon">!</span>
            <h3>{t('catalog.loadErrorTitle')}</h3>
            <p>{t('catalog.loadErrorDesc')}</p>
          </div>
        ) : coupons.length === 0 ? (
          <div className="catalog-empty">
            <span className="catalog-empty__icon">🔍</span>
            <h3>{t('catalog.emptyTitle')}</h3>
            <p>{t('catalog.emptyDesc')}</p>
          </div>
        ) : (
          <div className={`coupon-grid ${viewMode === 'list' ? 'coupon-grid--list' : ''}`}>
            {coupons.map((coupon) => (
              <CouponCard key={coupon.id} coupon={mapCouponOfferToCardData(coupon)} layout="card" />
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
              ← {t('common.back')}
            </button>
            <span className="pagination-info">
              {page + 1} / {totalPages}
            </span>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
              className="pagination-btn"
            >
              {t('common.next')} →
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
