import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { MapPin, Flame, SearchX } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { bazaarsApi } from '../api/bazaars';
import type { Shop } from '../api/bazaars';
import { couponsApi } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import SearchBar from '../components/ui/SearchBar';
import { useLocalePath } from '../hooks/useLocalePath';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
import './SearchPage.css';

type SearchShop = Shop & {
  hasCoupon?: boolean;
  productTags?: Array<{ tag: string }>;
};

export default function SearchDesktop() {
  const { t } = useTranslation();
  const lp = useLocalePath();
  // Поиск-оверлей в шапке передаёт запрос через ?q= — подхватываем его как начальное состояние
  const [searchParams] = useSearchParams();
  const initialQuery = searchParams.get('q') ?? '';
  const [query, setQuery] = useState(initialQuery);
  const [searchTerm, setSearchTerm] = useState(initialQuery);
  const navigate = useNavigate();

  const popularQueries = ['SPA', t('search.popular.pizza'), t('search.popular.fitness'), 'Sushi', t('search.popular.karting')];

  const { data: categories = [] } = useQuery({
    queryKey: ['search-categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
  });

  const { data: shops = [], isLoading: isLoadingShops } = useQuery({
    queryKey: ['shop-search', searchTerm],
    queryFn: () => bazaarsApi.searchShops(searchTerm),
    select: (res) => res.data.data,
    enabled: searchTerm.length >= 2,
  });

  const { data: coupons = [], isLoading: isLoadingCoupons } = useQuery({
    queryKey: ['coupon-search', searchTerm],
    queryFn: () => couponsApi.getCatalog({ search: searchTerm, size: 20 }),
    select: (res) => res.data.data.content,
    enabled: searchTerm.length >= 2,
  });

  const filteredCoupons = coupons.map(mapCouponOfferToCardData);

  const handleClear = () => {
    setQuery('');
    setSearchTerm('');
  };

  const applyPopular = (req: string) => {
    setQuery(req);
    setSearchTerm(req);
  };

  const isLoading = (isLoadingShops || isLoadingCoupons) && searchTerm.length >= 2;
  const isTyping = query !== searchTerm;

  return (
    <div className="search-page">
      <div className="container">
      <div className="search-header">
        <h1>{t('search.title')}</h1>
        <div className="search-page-bar-container">
          <SearchBar
            value={query}
            onChange={(val) => {
              setQuery(val);
              if (!val.trim()) {
                setSearchTerm('');
              }
            }}
            onSubmit={(val) => {
              const trimmed = val.trim();
              setSearchTerm(trimmed);
              setQuery(trimmed);
            }}
            placeholder={t('search.pagePlaceholder')}
            autoFocus
          />
        </div>
      </div>

      {!searchTerm && !isTyping && (
        <div className="search-suggestions surface-card">
          <div className="search-categories">
            <h3 className="search-suggestions__title">{t('search.popularCategories')}</h3>
            <div className="search-tags search-tags--scroll">
              {categories.map((c) => (
                <button
                  key={c.id}
                  type="button"
                  className="search-tag-btn"
                  onClick={() => applyPopular(c.name)}
                >
                  {c.iconUrl ? <span className="search-tag-btn__icon">{c.iconUrl}</span> : null}
                  {c.name}
                </button>
              ))}
            </div>
          </div>
          <h3 className="search-suggestions__title" style={{ marginTop: '24px' }}>
            <Flame size={18} color="var(--primary)" />
            {t('search.popularQueries')}
          </h3>
          <div className="search-tags">
            {popularQueries.map(q => (
              <button key={q} className="search-tag-btn" onClick={() => applyPopular(q)}>
                {q}
              </button>
            ))}
          </div>
        </div>
      )}

      {searchTerm && (
        <div className="search-results">
          {isLoading ? (
            <div className="search-loading">{t('search.loading')}</div>
          ) : (
            <>
              {filteredCoupons.length > 0 && (
                <div className="search-section">
                  <h2 className="search-section__title">{t('search.couponsSection')} <span className="badge">{filteredCoupons.length}</span></h2>
                  <div className="search-grid">
                    {filteredCoupons.map((coupon) => (
                      <CouponCard key={coupon.id} coupon={coupon} layout="card" />
                    ))}
                  </div>
                </div>
              )}

              {shops.length > 0 && (
                <div className="search-section">
                  <h2 className="search-section__title">{t('search.shopsSection')} <span className="badge">{shops.length}</span></h2>
                  <div className="search-shops-list">
                    {shops.map((shop: SearchShop) => {
                      const productTags = shop.productTags ?? [];

                      return (
                        <div key={shop.id} className="search-shop-card surface-card" onClick={() => navigate(lp(`/shop/${shop.id}`))}>
                          <div className="search-shop-card__main">
                            <h3>{shop.name}</h3>
                            {shop.bazaar && (
                              <p className="search-shop__bazaar">
                                <MapPin size={14} /> {shop.bazaar.name}  {shop.shopNumber ? `· № ${shop.shopNumber}` : ''}
                              </p>
                            )}
                          </div>
                          <div className="search-shop-card__meta">
                            {shop.hasCoupon && <span className="search-coupon-badge">{t('search.discountsBadge')}</span>}
                            {productTags.length > 0 && (
                              <p className="search-shop__tags-text">
                                {productTags.slice(0, 3).map((tag) => tag.tag).join(', ')}
                              </p>
                            )}
                          </div>
                        </div>
                      );
                    })}
                  </div>
                </div>
              )}

              {!isLoading && filteredCoupons.length === 0 && shops.length === 0 && (
                <div className="search-empty surface-card">
                  <SearchX className="search-empty-icon" size={56} strokeWidth={1.25} />
                  <h3>{t('search.emptyTitle')}</h3>
                  <p>{t('search.emptyDesc', { query: searchTerm })}</p>
                  <button className="text-button" onClick={handleClear}>{t('search.reset')}</button>
                </div>
              )}
            </>
          )}
        </div>
      )}
      </div>
    </div>
  );
}
