import { Sparkles } from 'lucide-react';
import DealCard from '../components/marketplace/DealCard';
import { useFavoritesStore } from '../store/favoritesStore';
import './FavoritesPage.css';

export default function FavoritesPage() {
  const { favorites } = useFavoritesStore();

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

      {favorites.length === 0 ? (
        <div className="favorites-empty surface-card" style={{ padding: '40px', textAlign: 'center', marginTop: '20px' }}>
          <h2>Пока ничего нет</h2>
          <p>Нажимайте на сердечки на купонах, чтобы сохранить их здесь</p>
        </div>
      ) : (
        <section className="favorites-grid">
          {favorites.map((deal) => (
            <div key={deal.id} className="favorites-grid__item">
              <DealCard deal={deal} />
            </div>
          ))}
        </section>
      )}
    </div>
  );
}
