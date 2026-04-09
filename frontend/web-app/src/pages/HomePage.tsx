import { useMemo, useState, useRef } from 'react';
import { ArrowRight, ChevronLeft, ChevronRight, MapPinned, Sparkles, Coffee, Scissors, Dumbbell, Gamepad2, Plane, Baby } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import heroImage from '../assets/images/hero-banner.png';
import { useQuery } from '@tanstack/react-query';
import { couponsApi } from '../api/coupons';
import type { Category } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import SearchBar from '../components/ui/SearchBar';
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

// Removed BANNERS array for new Hero section

export default function HomePage() {
  const { t, i18n } = useTranslation();
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

  const categories: Category[] = categoriesData || topdimCategories;
  const apiDeals = couponsData || [];

  const searchSuggestions = useMemo(() => {
    return categories.map((c) => ({
      id: c.id,
      label: i18n.language === 'uz' ? (c.nameUz || c.name) : c.name,
    }));
  }, [categories, i18n.language]);

  const mappedDeals: CouponCardData[] = useMemo(() => {
    if (apiDeals.length > 0) {
      return apiDeals.map((deal: any) => ({
        id: deal.id,
        title: deal.title,
        shortDescription: deal.shortDescription,
        merchant: deal.merchant || { id: 0, name: 'TopDim' },
        category: deal.category,
        oldPrice: deal.oldPrice,
        fromPrice: deal.fromPrice,
        discountPercent: deal.discountPercent,
        coverImageUrl: deal.coverImageUrl,
        totalSold: deal.totalSold || 0,
        rating: deal.rating || 4.5 + Math.random() * 0.4,
        reviewCount: deal.reviews || Math.floor((deal.totalSold || 0) * 0.3),
        address: deal.address,
        location: deal.address || 'Ташкент',
        isHot: (deal.discountPercent || 0) >= 50,
        countdownText: '23:59:59',
        giftAvailable: deal.giftAvailable,
      }));
    }
    return topdimDeals.map((d) => ({
      id: d.id,
      title: d.title,
      shortDescription: d.shortDescription,
      merchant: d.merchant,
      category: d.category,
      oldPrice: d.oldPrice,
      fromPrice: d.fromPrice,
      discountPercent: d.discountPercent,
      coverImageUrl: d.coverImageUrl || d.image,
      totalSold: d.totalSold,
      rating: d.rating,
      reviewCount: d.reviews,
      address: d.location,
      location: d.location,
      isHot: d.isHot,
      countdownText: d.countdownText,
      giftAvailable: d.giftAvailable,
    }));
  }, [apiDeals]);


  const filteredDeals = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();
    return mappedDeals.filter((deal) => {
      const matchesCategory = activeCategory === null || deal.category?.id === activeCategory;
      const haystack = `${deal.title} ${deal.shortDescription} ${deal.category?.name} ${deal.merchant?.name}`.toLowerCase();
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
            Твои лучшие моменты <br/>
            <span className="text-gradient">со скидкой до 90%</span>
          </h1>
          <p className="home-hero__subtitle">
            Каждый день уникальные предложения на кафе, рестораны, SPA, развлечения и спорт. Покупай эмоции выгодно.
          </p>
          <div className="home-hero__search">
            <SearchBar
              value={search}
              onChange={setSearch}
              placeholder={t('home.searchPlaceholder') || 'Поиск по категориям и заведениям...'}
              suggestions={searchSuggestions}
              onSuggestionClick={(id) => {
                setActiveCategory(id);
                setSearch('');
              }}
            />
          </div>
          <div className="home-hero__actions">
            <Link to="/coupons" className="primary-button home-hero__btn">
              Смотреть предложения
            </Link>
          </div>
        </div>
        <div className="home-hero__visual">
          <div className="home-hero__badge">
            <span className="home-hero__badge-icon">🔥</span>
            <div>
              <span className="home-hero__badge-title">Самый популярный</span>
              <span className="home-hero__badge-desc">Справка купон - самый сочный !!!</span>
            </div>
          </div>
          <div className="home-hero__visual-inner">
            <img 
              src={heroImage} 
              alt="Скидки" 
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
              {i18n.language === 'uz' ? (category.nameUz || category.name) : category.name}
            </button>
          ))}
        </div>
      </section>

      {/* ═══ Top Deals (Horizontal Carousel) ═══ */}
      {topDeals.length > 0 && (
        <section className="section container">
          <div className="section-heading">
            <div>
              <p className="section-label">🔥 Топ-акции дня</p>
              <h2 className="section-title">Забирают быстрее всего</h2>
            </div>
            <div className="carousel-controls">
              <button onClick={() => scrollCarousel(-1)} className="carousel-arrow" aria-label="Назад">
                <ChevronLeft size={18} />
              </button>
              <button onClick={() => scrollCarousel(1)} className="carousel-arrow" aria-label="Вперёд">
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
              <p className="section-label">✨ Новые</p>
              <h2 className="section-title">Только что добавлены</h2>
            </div>
            <Link to="/coupons?sortBy=new" className="section-link">
              Все новые
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
          <Link to="/coupons" className="section-link">
            Показать все
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
            <p className="section-label">Онлайн базар</p>
            <h2 className="section-title">Карта и товары рядом с тобой</h2>
            <p className="section-copy">
              Переключайся между пинами, товарами и расстоянием. Найди лучшие скидки на карте Ташкента.
            </p>
            <Link to="/bazaar" className="primary-button">
              Открыть базар
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
