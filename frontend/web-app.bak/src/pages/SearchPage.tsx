import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search as SearchIcon, MapPin, Tag } from 'lucide-react';
import { bazaarsApi } from '../api/bazaars';
import type { Shop } from '../api/bazaars';
import './SearchPage.css';

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const [searchTerm, setSearchTerm] = useState('');

  const { data: shops = [], isLoading } = useQuery({
    queryKey: ['shop-search', searchTerm],
    queryFn: () => bazaarsApi.searchShops(searchTerm),
    select: (res) => res.data.data,
    enabled: searchTerm.length >= 2,
  });

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchTerm(query);
  };

  return (
    <div className="search-page container">
      <h1 className="search-page__title">🔍 Поиск</h1>
      <p className="search-page__subtitle">Найдите магазины, товары и купоны</p>

      <form className="search-form" onSubmit={handleSearch}>
        <SearchIcon size={20} className="search-form__icon" />
        <input
          type="text"
          placeholder="Поиск по магазинам и товарам..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          className="search-form__input"
          autoFocus
        />
      </form>

      {searchTerm && (
        <div className="search-results">
          <h2 className="search-results__title">
            {isLoading ? 'Ищем...' : `Результаты (${shops.length})`}
          </h2>
          {shops.length === 0 && !isLoading && (
            <div className="search-empty">
              <p>Ничего не найдено по запросу "{searchTerm}"</p>
            </div>
          )}
          <div className="search-results__list">
            {shops.map((shop: Shop) => (
              <div key={shop.id} className="search-shop-card">
                <h3>{shop.name}</h3>
                {shop.bazaar && (
                  <p className="search-shop__bazaar">
                    <MapPin size={14} /> {shop.bazaar.name}
                  </p>
                )}
                {shop.productTags.length > 0 && (
                  <div className="search-shop__tags">
                    {shop.productTags.slice(0, 5).map((t, i) => (
                      <span key={i} className="search-tag">
                        <Tag size={12} /> {t.tag}
                      </span>
                    ))}
                  </div>
                )}
                {shop.hasCoupon && <span className="search-coupon-badge">🎫 Есть купон</span>}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
