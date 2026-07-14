import { useMemo, useState, useRef } from 'react';
import { ArrowRight, ChevronLeft, ChevronRight, MapPinned, Sparkles, Coffee, Scissors, Dumbbell, Gamepad2, Plane, Baby, Flame } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useLocalePath } from '../hooks/useLocalePath';
import heroImage from '../assets/images/hero-banner.png';
import { useQuery } from '@tanstack/react-query';
import { couponsApi } from '../api/coupons';
import type { Category, CouponOffer } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import SearchBar from '../components/ui/SearchBar';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
import { localizedName } from '../utils/localizedText';
import './HomePage.css';

const EMPTY_CATEGORIES: Category[] = [];
const EMPTY_COUPONS: CouponOffer[] = [];

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

// Removed BANNERS array for new Hero section

export default function HomePage() {
  const { t, i18n } = useTranslation();
  const lp = useLocalePath();
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  // Removed banner automatic sliding logic

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

  const categories: Category[] = categoriesData ?? EMPTY_CATEGORIES;
  const apiDeals = couponsData ?? EMPTY_COUPONS;

  const searchSuggestions = useMemo(() => {
    return categories.map((c) => ({
      id: c.id,
      label: localizedName(c, i18n.language),
    }));
  }, [categories, i18n.language]);

  const mappedDeals: CouponCardData[] = useMemo(() => {
    return apiDeals.map(mapCouponOfferToCardData);
  }, [apiDeals]);


  const filteredDeals = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();
    return mappedDeals.filter((deal) => {
      const matchesCategory = activeCategory === null || deal.category?.id === activeCategory;
      const haystack = `${deal.title} ${deal.shortDescription || ''} ${deal.offerDescription || ''} ${deal.category?.name || ''} ${deal.merchant?.name || ''}`.toLowerCase();
      const matchesSearch = normalizedSearch.length === 0 || haystack.includes(normalizedSearch);
      return matchesCategory && matchesSearch;
    });
  }, [mappedDeals, activeCategory, search]);

  const topDeals = filteredDeals.filter((d) => d.isHot || (d.totalSold && d.totalSold > 200)).slice(0, 10);
  const newDeals = filteredDeals.slice(0, 8); // Grab first 8 as "new"

  const carouselRef = useRef<HTMLDivElement>(null);
  const [isDragging, setIsDragging] = useState(false);
  const [startX, setStartX] = useState(0);
  const [scrollLeft, setScrollLeft] = useState(0);
  const isMouseDown = useRef(false);

  const scrollCarousel = (dir: number) => {
    if (carouselRef.current) {
      carouselRef.current.scrollBy({ left: dir * 320, behavior: 'smooth' });
    }
  };

  const handleMouseDown = (e: React.MouseEvent) => {
    if (!carouselRef.current) return;
    isMouseDown.current = true;
    setIsDragging(false);
    setStartX(e.pageX - carouselRef.current.offsetLeft);
    setScrollLeft(carouselRef.current.scrollLeft);
  };

  const handleMouseLeave = () => {
    isMouseDown.current = false;
    setIsDragging(false);
  };

  const handleMouseUp = () => {
    isMouseDown.current = false;
    setIsDragging(false);
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!isMouseDown.current || !carouselRef.current) return;
    const x = e.pageX - carouselRef.current.offsetLeft;
    const diff = Math.abs(x - startX);
    if (diff > 5) {
      setIsDragging(true);
      e.preventDefault();
      const walk = (x - startX) * 2;
      carouselRef.current.scrollLeft = scrollLeft - walk;
    }
  };

  return (
    <div className="home-page">
      {/* ═══ Hero Section ═══ */}
      <section className="home-hero container">
        <div className="home-hero__content">
          <h1 className="home-hero__title">
            {t('home.heroTitle')} <br/>
            <span className="text-gradient">{t('home.heroTitleHighlight')}</span>
          </h1>
          <p className="home-hero__subtitle">
            {t('home.heroSubtitle')}
          </p>
          <div className="home-hero__search">
            <SearchBar
              value={search}
              onChange={setSearch}
              placeholder={t('home.searchPlaceholder')}
              suggestions={searchSuggestions}
              onSuggestionClick={(id) => {
                setActiveCategory(id);
                setSearch('');
              }}
            />
          </div>
          <div className="home-hero__actions">
            <Link to={lp('/coupons')} className="primary-button home-hero__btn">
              {t('home.btnOffers')}
            </Link>
          </div>
        </div>
        <div className="home-hero__visual">
          <div className="home-hero__badge">
            <Flame className="home-hero__badge-icon" size={24} strokeWidth={1.75} />
            <div>
              <span className="home-hero__badge-title">{t('home.heroBadgeTitle')}</span>
              <span className="home-hero__badge-desc">{t('home.heroBadgeDesc')}</span>
            </div>
          </div>
          <div className="home-hero__visual-inner">
            <img 
              src={heroImage} 
              alt={t('home.heroImageAlt')}
              className="home-hero__img" 
            />
            <div className="home-hero__glow"></div>
          </div>
        </div>
      </section>


      {/* ═══ Categories ═══ */}
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
            className={`filter-chip ${activeCategory === null ? 'filter-chip--active' : ''}`}
            onClick={() => setActiveCategory(null)}
          >
            <span><Sparkles size={18} /></span>
            {t('home.categoryAll')}
          </button>
          {categories.map((category) => (
            <button
              key={category.id}
              type="button"
              className={`filter-chip ${activeCategory === category.id ? 'filter-chip--active' : ''}`}
              onClick={() => setActiveCategory(category.id)}
            >
              <span><CategoryIcon slug={category.slug} /></span>
              {localizedName(category, i18n.language)}
            </button>
          ))}
        </div>
      </section>

      {/* ═══ Top Deals (Horizontal Carousel) ═══ */}
      {topDeals.length > 0 && (
        <section className="section container">
          <div className="section-heading">
            <div>
              <p className="section-label">{t('home.topDealsLabel')}</p>
              <h2 className="section-title">{t('home.topDealsTitle')}</h2>
            </div>
            <div className="carousel-controls">
              <button onClick={() => scrollCarousel(-1)} className="carousel-arrow" aria-label={t('common.carouselPrev')}>
                <ChevronLeft size={18} />
              </button>
              <button onClick={() => scrollCarousel(1)} className="carousel-arrow" aria-label={t('common.carouselNext')}>
                <ChevronRight size={18} />
              </button>
            </div>
          </div>
          <div 
            className={`deals-carousel ${isDragging ? 'deals-carousel--dragging' : ''}`}
            ref={carouselRef}
            onMouseDown={handleMouseDown}
            onMouseLeave={handleMouseLeave}
            onMouseUp={handleMouseUp}
            onMouseMove={handleMouseMove}
          >
            {topDeals.map((deal) => (
              <CouponCard key={deal.id} coupon={deal} layout="carousel" />
            ))}
          </div>
        </section>
      )}

      {/* ═══ New Deals ═══ */}
      {newDeals.length > 0 && (
        <section className="section container">
          <div className="section-heading">
            <div>
              <p className="section-label">{t('home.newDealsLabel')}</p>
              <h2 className="section-title">{t('home.newDealsTitle')}</h2>
            </div>
            <Link to={lp('/coupons?sortBy=new')} className="section-link">
              {t('home.newDealsLink')}
              <ArrowRight size={16} />
            </Link>
          </div>
          <div className="deals-mosaic">
            {newDeals.map((deal) => (
              <CouponCard key={deal.id} coupon={deal} layout="card" />
            ))}
          </div>
        </section>
      )}

      {/* ═══ Main Grid ═══ */}
      <section className="section container" id="feed">
        <div className="section-heading">
          <div>
            <p className="section-label">{t('home.trendingLabel')}</p>
            <h2 className="section-title">{t('home.trendingTitle')}</h2>
          </div>
          <Link to={lp('/coupons')} className="section-link">
            {t('common.showAll')}
            <ArrowRight size={16} />
          </Link>
        </div>
        <div className="deals-mosaic">
          {filteredDeals.map((deal) => (
            <CouponCard key={deal.id} coupon={deal} layout="card" />
          ))}
        </div>
      </section>

      {/* ═══ Bazaar CTA ═══ */}
      <section className="section container">
        <div className="bazaar-cta surface-card">
          <div className="bazaar-cta__content">
            <p className="section-label">{t('home.bazaarCtaLabel')}</p>
            <h2 className="section-title">{t('home.bazaarCtaTitle')}</h2>
            <p className="section-copy">
              {t('home.bazaarCtaCopy')}
            </p>
            <Link to={lp('/bazaar')} className="primary-button">
              {t('home.bazaarCtaBtn')}
              <MapPinned size={18} />
            </Link>
          </div>
        </div>
      </section>

      {/* Empty state */}
      {filteredDeals.length === 0 && (
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
