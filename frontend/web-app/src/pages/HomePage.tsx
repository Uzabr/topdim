import { useMemo, useState } from 'react';
import { ArrowRight, Flame, MapPinned, Sparkles, Star, TimerReset, TrendingUp } from 'lucide-react';
import { Link } from 'react-router-dom';
import type { Category } from '../api/coupons';
import DealCard from '../components/marketplace/DealCard';
import SearchBar from '../components/marketplace/SearchBar';
import { topdimCategories, topdimDeals } from '../data/topdim';
import './HomePage.css';

const searchSuggestions = ['Бранч со скидкой', 'Фитнес рядом', 'Beauty today', 'Семейные развлечения'];

export default function HomePage() {
  const [activeCategory, setActiveCategory] = useState<number | null>(null);
  const [search, setSearch] = useState('');

  const categories: Category[] = topdimCategories;

  const filteredDeals = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();

    return topdimDeals.filter((deal) => {
      const matchesCategory = activeCategory === null || deal.category.id === activeCategory;
      const haystack = `${deal.title} ${deal.shortDescription} ${deal.category.name} ${deal.merchant.name}`.toLowerCase();
      const matchesSearch = normalizedSearch.length === 0 || haystack.includes(normalizedSearch);

      return matchesCategory && matchesSearch;
    });
  }, [activeCategory, search]);

  const featuredDeals = filteredDeals.slice(0, 4);
  const hotDeals = filteredDeals.filter((deal) => deal.isHot);
  const trendingDeals = filteredDeals.filter((deal) => deal.isTrending);

  return (
    <div className="home-page">
      <section className="home-hero container">
        <div className="home-hero__content">
          <div className="home-hero__eyebrow pill">
            <Sparkles size={16} />
            Скидки до 70% в Ташкенте
          </div>

          <h1 className="page-title">
            Лови hidden deals
            <span className="text-gradient"> быстрее других</span>
          </h1>

          <p className="home-hero__copy">
            Topdim превращает купоны и базар в addictive discovery feed: горячие предложения,
            быстрый дефицит и ощущение, что следующая находка будет ещё лучше.
          </p>

          <div className="home-hero__actions">
            <a href="#feed" className="primary-button">
              Найти скидку
              <ArrowRight size={18} />
            </a>
            <Link to="/bazaar" className="secondary-button">
              Перейти к базару
              <MapPinned size={18} />
            </Link>
          </div>

          <div className="home-hero__search">
            <SearchBar
              value={search}
              onChange={setSearch}
              placeholder="Что хочешь найти? (еда, фитнес, развлечения...)"
              suggestions={searchSuggestions}
              onSuggestionSelect={setSearch}
            />
          </div>

          <div className="home-hero__stats">
            <div className="surface-card">
              <strong>12k+</strong>
              <span>охотников за скидками</span>
            </div>
            <div className="surface-card">
              <strong>234</strong>
              <span>купили сегодня Terrace 360</span>
            </div>
            <div className="surface-card">
              <strong>48 мин</strong>
              <span>среднее время скролла</span>
            </div>
          </div>
        </div>

        <div className="home-hero__visual">
          <div className="home-hero__glow" />
          <div className="home-hero__sticker home-hero__sticker--one">-70%</div>
          <div className="home-hero__sticker home-hero__sticker--two">🔥 5 купонов</div>
          <div className="home-hero__sticker home-hero__sticker--three">234 купили сегодня</div>
          <DealCard deal={topdimDeals[0]} layout="featured" />
        </div>
      </section>

      <section className="section container">
        <div className="section-heading">
          <div>
            <p className="section-label">Категории</p>
            <h2 className="section-title">Свайпай по интересам</h2>
          </div>
        </div>

        <div className="categories-row">
          <button
            type="button"
            className={`category-bubble ${activeCategory === null ? 'category-bubble--active' : ''}`}
            onClick={() => setActiveCategory(null)}
          >
            <span>✨</span>
            Все
          </button>
          {categories.map((category) => (
            <button
              key={category.id}
              type="button"
              className={`category-bubble ${activeCategory === category.id ? 'category-bubble--active' : ''}`}
              onClick={() => setActiveCategory(category.id)}
            >
              <span>{category.iconUrl}</span>
              {category.name}
            </button>
          ))}
        </div>
      </section>

      <section className="section container" id="feed">
        <div className="section-heading">
          <div>
            <p className="section-label">Trending now</p>
            <h2 className="section-title">Лента, которую хочется листать дальше</h2>
          </div>
          <Link to="/coupons" className="section-link">
            Смотреть все
            <ArrowRight size={16} />
          </Link>
        </div>

        <div className="deals-mosaic">
          {featuredDeals.map((deal, index) => (
            <DealCard
              key={deal.id}
              deal={deal}
              layout={index === 0 ? 'featured' : index === 3 ? 'compact' : 'standard'}
            />
          ))}
        </div>
      </section>

      <section className="section container">
        <div className="info-ribbon surface-card">
          <div>
            <Flame size={18} />
            <strong>Горящие скидки</strong>
            <span>Обновляются каждые 15 минут</span>
          </div>
          <div>
            <TimerReset size={18} />
            <strong>Таймеры</strong>
            <span>Давят на FOMO, но красиво</span>
          </div>
          <div>
            <Star size={18} />
            <strong>Social proof</strong>
            <span>Отзывы, рейтинги, покупки сегодня</span>
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
        Найти скидку
      </a>

      {trendingDeals.length === 0 && (
        <section className="section container">
          <div className="empty-state surface-card">
            <h2 className="section-title">Пока пусто по этому фильтру</h2>
            <p className="section-copy">Попробуй другую категорию или быстрый поиск выше.</p>
          </div>
        </section>
      )}
    </div>
  );
}
