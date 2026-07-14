import { useState } from 'react';
import { Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { couponsApi } from '../api/coupons';
import type { Category, CouponOffer } from '../api/coupons';
import DealDeck from '../components/mobile/DealDeck';
import MobileCouponCard from '../components/mobile/MobileCouponCard';
import { useLensEffect } from '../hooks/useLensEffect';
import { useLocalePath } from '../hooks/useLocalePath';
import { localizedName } from '../utils/localizedText';
import './HomeMobile.css';

const EMPTY_CATEGORIES: Category[] = [];
const EMPTY_COUPONS: CouponOffer[] = [];

/** Первый чип — «Все»: без категории, лента по популярности. */
const ALL = 'all';

/** Карусель (колода + 3 карточки) + сетка: 3 + 8 = ровно 4 ряда по 2. */
const FEED_SIZE = 11;
const FEATURED = 3;
/** Сколько горящих купонов лежит в колоде «Купон дня». */
const DECK_SIZE = 5;

export default function HomeMobile() {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const [tab, setTab] = useState<number | typeof ALL>(ALL);

  const { data: categoriesData } = useQuery({
    queryKey: ['categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
  });
  const categories = categoriesData ?? EMPTY_CATEGORIES;

  // Колода — самые большие скидки; показываем только в разделе «Все».
  const { data: dealsData } = useQuery({
    queryKey: ['coupons', 'deck'],
    queryFn: () => couponsApi.getCatalog({ sortBy: 'discount', size: DECK_SIZE }),
    select: (res) => res.data.data.content,
    enabled: tab === ALL,
  });
  const deals = tab === ALL ? (dealsData ?? EMPTY_COUPONS) : EMPTY_COUPONS;

  const { data: feedData, isLoading } = useQuery({
    queryKey: ['coupons', 'mobile-feed', tab],
    queryFn: () =>
      couponsApi.getCatalog({
        sortBy: 'popular',
        size: FEED_SIZE,
        ...(tab === ALL ? {} : { categoryId: tab }),
      }),
    select: (res) => res.data.data.content,
  });
  const feed = feedData ?? EMPTY_COUPONS;

  const featured = feed.slice(0, FEATURED);
  const rest = feed.slice(FEATURED);

  // Лупа читает DOM, поэтому включаем её только когда карточки уже отрисованы.
  useLensEffect(feed.length > 0);

  return (
    <div className="mhome">
      <div className="mhome__top">
        <h1 className="mhome__title">{t('mobile.home.title')}</h1>

        <Link to={lp('/search')} className="mhome__search" aria-label={t('common.search')}>
          <Search size={23} strokeWidth={1.8} />
        </Link>
      </div>

      <div className="mhome__chips">
        <button
          type="button"
          className={`mchip${tab === ALL ? ' mchip--active' : ''}`}
          onClick={() => setTab(ALL)}
        >
          {t('common.all')}
        </button>
        {categories.map((category) => (
          <button
            key={category.id}
            type="button"
            className={`mchip${tab === category.id ? ' mchip--active' : ''}`}
            onClick={() => setTab(category.id)}
          >
            {localizedName(category, i18n.language)}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="mhome__empty">{t('common.loading')}</p>
      ) : feed.length === 0 ? (
        <p className="mhome__empty">{t('mobile.home.empty')}</p>
      ) : (
        <>
          <div className="mhome__row">
            <DealDeck deals={deals} />
            {featured.map((coupon) => (
              <MobileCouponCard key={coupon.id} coupon={coupon} variant="carousel" />
            ))}
          </div>

          <div className="mhome__grid">
            {rest.map((coupon) => (
              <MobileCouponCard key={coupon.id} coupon={coupon} variant="grid" />
            ))}
          </div>
        </>
      )}
    </div>
  );
}
