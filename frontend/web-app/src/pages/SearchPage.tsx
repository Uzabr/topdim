import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { MapPin, Flame } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { bazaarsApi } from '../api/bazaars';
import type { Shop } from '../api/bazaars';
import CouponCard from '../components/coupon/CouponCard';
import SearchBar from '../components/ui/SearchBar';
import { topdimCategories } from '../data/topdim';
import { DEMO_COUPONS } from './CouponCatalogPage';
import './SearchPage.css';

const POPULAR_QUERIES = ['SPA', 'Пицца', 'Фитнес', 'Sushi', 'Картинг'];

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const navigate = useNavigate();

  const { data: shops = [], isLoading: isLoadingShops } = useQuery({
    queryKey: ['shop-search', searchTerm],
    queryFn: () => bazaarsApi.searchShops(searchTerm),
    select: (res) => res.data.data,
    enabled: searchTerm.length >= 2,
  });

  // Mock coupon search (until backend is ready)
  const filteredCoupons = searchTerm.length >= 2 
    ? DEMO_COUPONS.filter((c: any) => 
        c.title.toLowerCase().includes(searchTerm.toLowerCase()) || 
        c.merchant.name.toLowerCase().includes(searchTerm.toLowerCase())
      )
    : [];

  const handleClear = () => {
    setQuery('');
    setSearchTerm('');
  };

  const applyPopular = (req: string) => {
    setQuery(req);
    setSearchTerm(req);
  };

  const isLoading = isLoadingShops && searchTerm.length >= 2;
  const isTyping = query !== searchTerm;

  return (
    <div className="search-page container">
      <div className="search-header">
        <h1>Поиск</h1>
        <div className="search-page-bar-container">
          <SearchBar
            value={query}
            onChange={setQuery}
            onSubmit={(val) => {
              if (val.trim()) setSearchTerm(val.trim());
            }}
            placeholder="Искать скидки, магазины или услуги..."
            autoFocus
          />
        </div>
      </div>

      {!searchTerm && !isTyping && (
        <div className="search-suggestions glass-card">
          <div className="search-categories">
            <h3 className="search-suggestions__title">Популярные категории</h3>
            <div className="categories-grid">
              {topdimCategories.map((c) => (
                <div key={c.id} className="category-card" onClick={() => applyPopular(c.name)}>
                  <span className="category-card__icon">{c.iconUrl}</span>
                  <span className="category-card__name">{c.name}</span>
                </div>
              ))}
            </div>
          </div>
          <h3 className="search-suggestions__title" style={{ marginTop: '24px' }}>
            <Flame size={18} color="var(--primary)" />
            Популярные запросы
          </h3>
          <div className="search-tags">
            {POPULAR_QUERIES.map(q => (
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
            <div className="search-loading">Поиск лучших предложений...</div>
          ) : (
            <>
              {/* Coupons Results */}
              {filteredCoupons.length > 0 && (
                <div className="search-section">
                  <h2 className="search-section__title">Акции и купоны <span className="badge">{filteredCoupons.length}</span></h2>
                  <div className="search-grid">
                    {filteredCoupons.map((coupon: any) => (
                      <CouponCard key={coupon.id} coupon={coupon} layout="card" />
                    ))}
                  </div>
                </div>
              )}

              {/* Shops Results */}
              {shops.length > 0 && (
                <div className="search-section">
                  <h2 className="search-section__title">Магазины на базарах <span className="badge">{shops.length}</span></h2>
                  <div className="search-shops-list">
                    {shops.map((shop: Shop) => (
                      <div key={shop.id} className="search-shop-card glass-card" onClick={() => navigate(`/shop/${shop.id}`)}>
                        <div className="search-shop-card__main">
                          <h3>{shop.name}</h3>
                          {shop.bazaar && (
                            <p className="search-shop__bazaar">
                              <MapPin size={14} /> {shop.bazaar.name}  {shop.shopNumber ? `· № ${shop.shopNumber}` : ''}
                            </p>
                          )}
                        </div>
                        <div className="search-shop-card__meta">
                          {(shop as any).hasCoupon && <span className="search-coupon-badge">🎫 Скидки</span>}
                          {((shop as any).productTags || []).length > 0 && (
                            <p className="search-shop__tags-text">
                              {(shop as any).productTags.slice(0, 3).map((t: any) => t.tag).join(', ')}
                            </p>
                          )}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* Empty State */}
              {!isLoading && filteredCoupons.length === 0 && shops.length === 0 && (
                <div className="search-empty glass-card">
                  <span className="search-empty-icon">🕵️</span>
                  <h3>Упс, мы ничего не нашли</h3>
                  <p>По запросу «{searchTerm}» нет результатов. Попробуйте изменить запрос или поискать в других категориях.</p>
                  <button className="text-button" onClick={handleClear}>Сбросить поиск</button>
                </div>
              )}
            </>
          )}
        </div>
      )}
    </div>
  );
}
