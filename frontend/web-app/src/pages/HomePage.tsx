import { useMemo, useState } from 'react';
import { ArrowRight, Flame, MapPinned, Sparkles, Star, TimerReset, TrendingUp, Coffee, Scissors, Dumbbell, Gamepad2, Plane, Baby } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { couponsApi } from '../api/coupons';
import type { Category } from '../api/coupons';
import DealCard from '../components/marketplace/DealCard';
import SearchBar from '../components/marketplace/SearchBar';
import { topdimCategories, topdimDeals } from '../data/topdim';
import './HomePage.css';

const CategoryIcon = ({ slug }: { slug?: string }) => {
  switch (slug) {
    case 'food': return <Coffee size={18} />;
    case 'beauty': return <Scissors size={18} />;
    case 'sport': return <Dumbbell size={18} />;
    case 'entertainment': return <Gamepad2 size={18} />;
    case 'travel': return <Plane size={18} />;
    case 'kids': return <Baby size={18} />;
    default: return <Sparkles size={18} />;
  }
};

export default function HomePage() {
  const { t, i18n } = useTranslation();
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [search, setSearch] = useState('');

  const { data: categoriesData } = useQuery({
    queryKey: ['categories'],
    queryFn: () => couponsApi.getCategories(),
    select: (res) => res.data.data,
  });

  const { data: couponsData } = useQuery({
    queryKey: ['coupons-home'],
    queryFn: () => couponsApi.getCatalog({ size: 50 }),
    select: (res) => res.data.data.content,
  });

  const categories: Category[] = categoriesData || topdimCategories;
  const apiDeals = couponsData || [];

  const mappedDeals = useMemo(() => {
    // Map backend CouponOffer to UI TopdimDeal requirements
    return apiDeals.map((deal: any) => ({
      ...deal,
      rating: deal.rating || 4.8,
      reviews: deal.reviews || 0,
      stockLeft: deal.options?.[0]?.quantityLimit || 10,
      boughtToday: deal.totalSold || 0,
      countdownText: '23:59:59',
      location: deal.address || 'Ташкент',
      vibe: deal.category?.name || '',
      image: deal.coverImageUrl || '',
      isHot: (deal.discountPercent || 0) >= 50,
      isTrending: (deal.viewCount || 0) > 10,
    }));
  }, [apiDeals]);

  const computedSuggestions = useMemo(() => {
    const rawSearch = search.trim().toLowerCase();
    if (!rawSearch) return [];
    return categories
      .map(c => c.name)
      .filter(name => name.toLowerCase().includes(rawSearch));
  }, [search, categories]);

  const filteredDeals = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();

    const baseDeals = mappedDeals.length > 0 ? mappedDeals : topdimDeals.map(d => ({ ...d, image: d.coverImageUrl || d.image }));

    return baseDeals.filter((deal: any) => {
      const matchesCategory = activeCategory === null || deal.category?.id === activeCategory;
      const haystack = `${deal.title} ${deal.shortDescription} ${deal.category?.name} ${deal.merchant?.name}`.toLowerCase();
      const matchesSearch = normalizedSearch.length === 0 || haystack.includes(normalizedSearch);

      return matchesCategory && matchesSearch;
    });
  }, [mappedDeals, activeCategory, search]);

  const hotDeals = filteredDeals.filter((deal) => deal.isHot);
  const trendingDeals = filteredDeals.filter((deal) => deal.isTrending);

  return (
    <div className="home-page">
      <section className="home-hero container">
        <div className="home-hero__content">
          <div className="home-hero__eyebrow pill">
            <Sparkles size={16} />
            {t('home.heroEyebrow')}
          </div>

          <h1 className="page-title">
            {t('home.heroTitle')}
            <span className="text-gradient"> {t('home.heroTitleHighlight')}</span>
          </h1>

          <p className="home-hero__copy">
            {t('home.heroCopy')}
          </p>

          <div className="home-hero__actions">
            <a href="#feed" className="primary-button">
              {t('home.btnSearch')}
              <ArrowRight size={18} />
            </a>
            <Link to="/bazaar" className="secondary-button">
              {t('home.btnBazaar')}
              <MapPinned size={18} />
            </Link>
          </div>

          <div className="home-hero__search">
            <SearchBar
              value={search}
              onChange={setSearch}
              placeholder={t('home.searchPlaceholder')}
              suggestions={computedSuggestions}
              onSuggestionSelect={setSearch}
            />
          </div>

          <div className="home-hero__stats">
            <div className="surface-card">
              <strong>12k+</strong>
              <span>{t('home.statsUsers')}</span>
            </div>
            <div className="surface-card">
              <strong>234</strong>
              <span>{t('home.statsBought')}</span>
            </div>
            <div className="surface-card">
              <strong>48 мин</strong>
              <span>{t('home.statsScroll')}</span>
            </div>
          </div>
        </div>

        <div className="home-hero__visual">
          <div className="home-hero__glow" />
          <div className="home-hero__sticker home-hero__sticker--one">-70%</div>
          <div className="home-hero__sticker home-hero__sticker--two">🔥 5 купонов</div>
          <div className="home-hero__sticker home-hero__sticker--three">234 купили сегодня</div>
          {filteredDeals.length > 0 && <DealCard deal={filteredDeals[0]} layout="featured" />}
        </div>
      </section>

      <section className="section container">
        <div className="section-heading">
          <div>
            <p className="section-label">{t('home.categoriesLabel')}</p>
            <h2 className="section-title">{t('home.categoriesTitle')}</h2>
          </div>
        </div>

        <div className="categories-row">
          <button
            type="button"
            className={`category-bubble ${activeCategory === null ? 'category-bubble--active' : ''}`}
            onClick={() => setActiveCategory(null)}
          >
            <span><Sparkles size={18} /></span>
            {t('home.categoryAll')}
          </button>
          {categories.map((category) => (
            <button
              key={category.id}
              type="button"
              className={`category-bubble ${activeCategory === category.id ? 'category-bubble--active' : ''}`}
              onClick={() => setActiveCategory(category.id)}
            >
              <span><CategoryIcon slug={category.slug} /></span>
              {i18n.language === 'uz' ? (category.nameUz || category.name) : category.name}
            </button>
          ))}
        </div>
      </section>

      <section className="section container" id="feed">
        <div className="section-heading">
          <div>
            <p className="section-label">{t('home.trendingLabel')}</p>
            <h2 className="section-title">{t('home.trendingTitle')}</h2>
          </div>
        </div>

        <div className="deals-mosaic">
          {filteredDeals.map((deal) => (
            <DealCard
              key={deal.id}
              deal={deal}
              layout="standard"
            />
          ))}
        </div>
      </section>

      <section className="section container">
        <div className="info-ribbon surface-card">
          <div>
            <Flame size={18} />
            <strong>{t('home.hotLabel')}</strong>
            <span>{t('home.hotDesc')}</span>
          </div>
          <div>
            <TimerReset size={18} />
            <strong>{t('home.timers')}</strong>
            <span>{t('home.timersDesc')}</span>
          </div>
          <div>
            <Star size={18} />
            <strong>{t('home.socialProof')}</strong>
            <span>{t('home.socialProofDesc')}</span>
          </div>
        </div>
      </section>

      <section className="section container">
        <div className="section-heading">
          <div>
            <p className="section-label">🔥 Горящие скидки</p>
            <h2 className="section-title">Забирают быстрее всего</h2>
          </div>
        </div>

        <div className="deals-carousel">
          {hotDeals.map((deal) => (
            <DealCard key={deal.id} deal={deal} layout="compact" />
          ))}
        </div>
      </section>

      <section className="section container">
        <div className="bazaar-spotlight surface-card">
          <div className="bazaar-spotlight__copy">
            <p className="section-label">Онлайн базар</p>
            <h2 className="section-title">Карта и товары рядом с тобой</h2>
            <p className="section-copy">
              Переключайся между пинами, товарами и расстоянием. Чувствуется как TikTok feed,
              но для реальных покупок в Ташкенте.
            </p>
            <div className="bazaar-spotlight__points">
              <span>
                <TrendingUp size={16} />
                Пины с реальными скидками
              </span>
              <span>
                <MapPinned size={16} />
                “Показать рядом со мной”
              </span>
            </div>
            <Link to="/bazaar" className="primary-button">
              Открыть базар
              <ArrowRight size={18} />
            </Link>
          </div>

          <div className="bazaar-spotlight__visual">
            <div className="bazaar-spotlight__mock">
              <span className="bazaar-spotlight__chip">-35% рядом</span>
              <span className="bazaar-spotlight__chip">Beauty drop</span>
              <span className="bazaar-spotlight__chip">Grid / List</span>
            </div>
          </div>
        </div>
      </section>

      <a href="#feed" className="home-sticky-cta">
        {t('home.btnSearch')}
      </a>

      {trendingDeals.length === 0 && (
        <section className="section container">
          <div className="empty-state surface-card">
            <h2 className="section-title">{t('home.emptyState')}</h2>
            <p className="section-copy">{t('home.emptyStateDesc')}</p>
          </div>
        </section>
      )}
    </div>
  );
}
