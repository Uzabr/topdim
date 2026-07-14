import { useMemo, useState } from 'react';
import { useQueries, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { couponsApi } from '../api/coupons';
import type { Category, CouponOffer } from '../api/coupons';
import CategoryTiles from '../components/home/CategoryTiles';
import HeroCoupon from '../components/home/HeroCoupon';
import HowItWorks from '../components/home/HowItWorks';
import TetrisFeed from '../components/home/TetrisFeed';
import { localizedName } from '../utils/localizedText';
import './HomePage.css';

const EMPTY_CATEGORIES: Category[] = [];
const EMPTY_COUPONS: CouponOffer[] = [];

/** Первый таб ленты — не категория, а «самые большие скидки». */
const HOT_TAB = 'hot';

/** Тайл + блок из 4 фигур + блок из 5 → мозаика без хвоста. */
const FEED_SIZE = 10;

export default function HomeDesktop() {
  const { t, i18n } = useTranslation();
  const [tab, setTab] = useState<number | typeof HOT_TAB>(HOT_TAB);

  const { data: categoriesData } = useQuery({
    queryKey: ['categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
  });
  const categories = categoriesData ?? EMPTY_CATEGORIES;

  // Купон дня = самая большая скидка среди активных
  const { data: heroCoupon } = useQuery({
    queryKey: ['coupons', 'hero'],
    queryFn: () => couponsApi.getCatalog({ sortBy: 'discount', size: 1 }),
    select: (res) => res.data.data.content[0] ?? null,
  });

  // Счётчики для плиток: size=1, нужен только totalElements
  const countQueries = useQueries({
    queries: categories.map((c) => ({
      queryKey: ['coupons', 'count', c.id],
      queryFn: () => couponsApi.getCatalog({ categoryId: c.id, size: 1 }),
      select: (res: Awaited<ReturnType<typeof couponsApi.getCatalog>>) =>
        res.data.data.totalElements,
    })),
  });

  const counts = useMemo(() => {
    const map: Record<number, number | undefined> = {};
    categories.forEach((c, i) => {
      map[c.id] = countQueries[i]?.data;
    });
    return map;
  }, [categories, countQueries]);

  // Лента: «Горящие» — по скидке, категория — по популярности.
  const { data: feedData, isLoading: feedLoading } = useQuery({
    queryKey: ['coupons', 'feed', tab],
    queryFn: () =>
      couponsApi.getCatalog(
        tab === HOT_TAB
          ? { sortBy: 'discount', size: FEED_SIZE }
          : { categoryId: tab, sortBy: 'popular', size: FEED_SIZE },
      ),
    select: (res) => res.data.data.content,
  });
  const feed = feedData ?? EMPTY_COUPONS;

  return (
    <div className="home container">
      {heroCoupon && <HeroCoupon coupon={heroCoupon} />}

      <CategoryTiles categories={categories} counts={counts} />

      <section className="feed">
        <h2 className="feed__title">{t('home.feed.title')}</h2>

        <div className="feed__tabs">
          <button
            type="button"
            className={`feed__tab${tab === HOT_TAB ? ' feed__tab--active' : ''}`}
            onClick={() => setTab(HOT_TAB)}
          >
            {t('home.feed.hotTab')}
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              type="button"
              className={`feed__tab${tab === c.id ? ' feed__tab--active' : ''}`}
              onClick={() => setTab(c.id)}
            >
              {localizedName(c, i18n.language)}
            </button>
          ))}
        </div>

        {feedLoading ? (
          <p className="feed__empty">{t('common.loading')}</p>
        ) : feed.length === 0 ? (
          <p className="feed__empty">{t('home.feed.empty')}</p>
        ) : (
          <TetrisFeed coupons={feed} lead={tab === HOT_TAB ? 'discount' : 'popular'} />
        )}
      </section>

      <HowItWorks />
    </div>
  );
}
