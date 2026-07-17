import { useState } from 'react';
import { ChevronLeft, Search, X } from 'lucide-react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useQueries, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { couponsApi } from '../api/coupons';
import { useLocalePath } from '../hooks/useLocalePath';
import { calcDiscount } from '../utils/format';
import { SITUATIONS } from '../data/situations';
import './SearchMobile.css';

/** Бэкенд ищет от 2 символов — раньше запрос бессмыслен. */
const MIN_QUERY = 2;

export default function SearchMobile() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const lp = useLocalePath();
  const locale = i18n.language === 'uz' ? 'uz-UZ' : 'ru-RU';

  const [params] = useSearchParams();
  const [query, setQuery] = useState(params.get('q') ?? '');
  const term = query.trim();

  // Плитки ситуаций «Что хотите сегодня?» (Мобилка-9): счётчик — реальный поиск.
  const counts = useQueries({
    queries: SITUATIONS.map((s) => ({
      queryKey: ['coupons', 'situation-count', s.query],
      queryFn: () => couponsApi.getCatalog({ search: s.query, size: 1 }),
      select: (res: Awaited<ReturnType<typeof couponsApi.getCatalog>>) =>
        res.data.data.totalElements,
    })),
  });

  const { data: results = [], isLoading } = useQuery({
    queryKey: ['coupon-search', term],
    queryFn: () => couponsApi.getCatalog({ search: term, size: 20 }),
    select: (res) => res.data.data.content,
    enabled: term.length >= MIN_QUERY,
  });

  const suggestions = t('header.searchSuggestions', { returnObjects: true }) as string[];
  const searching = term.length >= MIN_QUERY;

  return (
    <div className="smob">
      <div className="smob__bar">
        <button
          type="button"
          className="mround mround--glass"
          onClick={() => (window.history.length > 1 ? navigate(-1) : navigate(lp('/')))}
          aria-label={t('common.back')}
        >
          <ChevronLeft size={18} strokeWidth={2} />
        </button>

        <div className="smob__field">
          <Search size={18} strokeWidth={1.8} className="smob__field-icon" />
          <input
            type="search"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('header.searchPlaceholder')}
            aria-label={t('common.search')}
            autoFocus
          />
          {query && (
            <button
              type="button"
              className="smob__clear"
              onClick={() => setQuery('')}
              aria-label={t('common.clear')}
            >
              <X size={12} />
            </button>
          )}
        </div>
      </div>

      {searching ? (
        <div className="smob__results">
          {isLoading ? (
            <p className="smob__empty">{t('search.loading')}</p>
          ) : results.length === 0 ? (
            <p className="smob__empty">{t('search.emptyDesc', { query: term })}</p>
          ) : (
            results.map((coupon) => {
              const off =
                coupon.discountPercent || calcDiscount(coupon.oldPrice ?? 0, coupon.fromPrice);
              return (
                <Link key={coupon.id} to={lp(`/coupons/${coupon.id}`)} className="srow">
                  <span className="srow__photo">
                    {coupon.coverImageUrl && (
                      <img src={coupon.coverImageUrl} alt="" loading="lazy" />
                    )}
                  </span>

                  <span className="srow__text">
                    <span className="srow__title">{coupon.title}</span>
                    <span className="srow__price">
                      {t('common.from')} {coupon.fromPrice.toLocaleString(locale)}{' '}
                      {t('common.currency.sum')}
                    </span>
                  </span>

                  {off > 0 && <span className="srow__off">−{off}%</span>}
                </Link>
              );
            })
          )}
        </div>
      ) : (
        <>
          <h1 className="smob__title">{t('home.tiles.title')}</h1>

          <div className="smob__tiles">
            {SITUATIONS.map((situation, i) => {
              const count = counts[i]?.data;
              return (
                <button
                  key={situation.key}
                  type="button"
                  onClick={() => setQuery(situation.query)}
                  className={`stile${situation.featured ? ' stile--featured' : ''}`}
                >
                  <span className="stile__name">{t(`home.situations.${situation.key}`)}</span>
                  {count !== undefined && count > 0 && (
                    <span className="stile__count">{t('home.tiles.count', { count })}</span>
                  )}
                </button>
              );
            })}
          </div>

          <h2 className="smob__subtitle">{t('header.frequentlySearched')}</h2>

          <div className="smob__chips">
            {suggestions.map((item) => (
              <button
                key={item}
                type="button"
                className="smob__chip"
                onClick={() => setQuery(item)}
              >
                {item}
              </button>
            ))}
          </div>
        </>
      )}
    </div>
  );
}
