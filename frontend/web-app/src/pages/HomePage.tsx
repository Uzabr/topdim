import { useMemo, useState, useRef, useEffect } from 'react';
import { ArrowRight, ChevronLeft, ChevronRight, MapPinned, Sparkles, Coffee, Scissors, Dumbbell, Gamepad2, Plane, Baby } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { couponsApi } from '../api/coupons';
import type { Category } from '../api/coupons';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
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

/* Placeholder banners */
const BANNERS = [
  {
    id: 1,
    title: 'SPA программы\nсо скидкой до 50%',
    cta: 'Посмотреть подборку',
    gradient: 'linear-gradient(135deg, #ff9a3d 0%, #ff5d5d 100%)',
    link: '/coupons?category=beauty',
  },
  {
    id: 2,
    title: 'Развлечения для всей\nсемьи со скидкой до 60%',
    cta: 'Выбрать купон',
    gradient: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    link: '/coupons?category=entertainment',
  },
  {
    id: 3,
    title: 'Фитнес и спорт\nот 99 000 сум',
    cta: 'В каталог',
    gradient: 'linear-gradient(135deg, #11998e 0%, #38ef7d 100%)',
    link: '/coupons?category=sport',
  },
];

export default function HomePage() {
  const { t, i18n } = useTranslation();
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [search, setSearch] = useState('');
  const [bannerIndex, setBannerIndex] = useState(0);
  const carouselRef = useRef<HTMLDivElement>(null);

  // Auto-rotate banners
  useEffect(() => {
    const timer = setInterval(() => {
      setBannerIndex((i) => (i + 1) % BANNERS.length);
    }, 5000);
    return () => clearInterval(timer);
  }, []);

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

  const computedSuggestions = useMemo(() => {
    const rawSearch = search.trim().toLowerCase();
    if (!rawSearch) return [];
    return categories
      .map(c => c.name)
      .filter(name => name.toLowerCase().includes(rawSearch));
  }, [search, categories]);

  const filteredDeals = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();
    return mappedDeals.filter((deal) => {
      const matchesCategory = activeCategory === null || deal.category?.id === activeCategory;
      const haystack = `${deal.title} ${deal.shortDescription} ${deal.category?.name} ${deal.merchant?.name}`.toLowerCase();
      const matchesSearch = normalizedSearch.length === 0 || haystack.includes(normalizedSearch);
      return matchesCategory && matchesSearch;
    });
  }, [mappedDeals, activeCategory, search]);

  const topDeals = filteredDeals.filter((d) => d.isHot || (d.totalSold && d.totalSold > 200));
  const newDeals = filteredDeals.slice(-6);

  const scrollCarousel = (dir: number) => {
    if (carouselRef.current) {
      carouselRef.current.scrollBy({ left: dir * 320, behavior: 'smooth' });
    }
  };

  return (
    <div className="home-page">
      {/* ═══ Banner Section ═══ */}
      <section className="home-banners container">
        <div className="home-banners__track">
          {BANNERS.map((banner, i) => (
            <Link
              key={banner.id}
              to={banner.link}
              className={`home-banner ${i === bannerIndex ? 'home-banner--active' : ''}`}
              style={{ background: banner.gradient }}
            >
              <div className="home-banner__content">
                <h2>{banner.title}</h2>
                <span className="home-banner__cta">{banner.cta}</span>
              </div>
            </Link>
          ))}
        </div>
        <div className="home-banners__dots">
          {BANNERS.map((_, i) => (
            <button
              key={i}
              className={`home-banners__dot ${i === bannerIndex ? 'home-banners__dot--active' : ''}`}
              onClick={() => setBannerIndex(i)}
            />
          ))}
        </div>
      </section>

      {/* ═══ Search ═══ */}
      <section className="home-search container">
        <SearchBar
          value={search}
          onChange={setSearch}
          placeholder={t('home.searchPlaceholder')}
          suggestions={computedSuggestions}
          onSuggestionSelect={setSearch}
        />
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
          <div className="deals-carousel" ref={carouselRef}>
            {topDeals.map((deal) => (
              <CouponCard key={deal.id} coupon={deal} layout="carousel" />
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
