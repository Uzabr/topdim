import { Heart, Sparkles } from 'lucide-react';
import DealCard from '../components/marketplace/DealCard';
import { topdimDeals } from '../data/topdim';
import './FavoritesPage.css';

export default function FavoritesPage() {
  const savedDeals = topdimDeals.slice(1, 4);

  return (
    <div className="favorites-page container">
      <section className="favorites-hero surface-card">
        <div className="pill favorites-hero__pill">
          <Sparkles size={16} />
          Твои сохранённые находки
        </div>
        <h1 className="page-title">Избранное для быстрого возврата к лучшим скидкам</h1>
        <p className="section-copy">
          Здесь живут купоны, к которым хочется вернуться: когда цена хорошая, а решение уже почти принято.
        </p>
      </section>

      <section className="favorites-grid">
        {savedDeals.map((deal) => (
          <div key={deal.id} className="favorites-grid__item">
            <Heart size={18} />
            <DealCard deal={deal} />
          </div>
        ))}
      </section>
    </div>
  );
}
