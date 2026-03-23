import { useQuery } from '@tanstack/react-query';
import { MapPin, Phone, Clock, ArrowLeft } from 'lucide-react';
import { Link } from 'react-router-dom';
import { bazaarsApi } from '../api/bazaars';
import type { Bazaar } from '../api/bazaars';
import './BazaarMapPage.css';

const DEMO_BAZAARS: Bazaar[] = [
  { id: 1, name: 'Чорсу базар', type: 'BAZAAR', address: 'ул. Навои, Ташкент', city: 'Ташкент',
    latitude: 41.3279, longitude: 69.2341, description: 'Старейший базар Ташкента',
    workingHours: '07:00-18:00', phone: '+998 71 241 44 44' },
  { id: 2, name: 'Tashkent City Mall', type: 'SHOPPING_CENTER', address: 'ул. Мустакиллик, 1',
    city: 'Ташкент', latitude: 41.3111, longitude: 69.2797, description: 'Крупнейший ТРЦ',
    workingHours: '10:00-22:00', phone: '+998 71 200 00 00' },
  { id: 3, name: 'Куйлюк базар', type: 'BAZAAR', address: 'Куйлюкский район', city: 'Ташкент',
    latitude: 41.2983, longitude: 69.3416, description: 'Оптовый рынок',
    workingHours: '06:00-17:00', phone: '+998 71 265 00 00' },
  { id: 4, name: 'Samarkand Darvoza', type: 'SHOPPING_CENTER', address: 'пр. Амира Темура',
    city: 'Ташкент', latitude: 41.3116, longitude: 69.2687, description: 'Торговый комплекс',
    workingHours: '09:00-21:00', phone: '+998 71 233 33 33' },
];

const typeLabels: Record<string, string> = {
  BAZAAR: 'Базар',
  SHOPPING_CENTER: 'ТЦ',
  MARKET: 'Рынок',
  TRADE_COMPLEX: 'ТК',
};

const typeColors: Record<string, string> = {
  BAZAAR: '#f59e0b',
  SHOPPING_CENTER: '#6366f1',
  MARKET: '#10b981',
  TRADE_COMPLEX: '#ef4444',
};

export default function BazaarMapPage() {
  const { data: bazaarsData } = useQuery({
    queryKey: ['bazaars'],
    queryFn: () => bazaarsApi.getAll(),
    select: (res) => res.data.data,
  });

  const bazaars = bazaarsData || DEMO_BAZAARS;

  return (
    <div className="bazaar-page">
      <div className="bazaar-page__header container">
        <h1 className="bazaar-page__title">
          🗺️ Онлайн Базар
        </h1>
        <p className="bazaar-page__subtitle">
          Базары, ТЦ и рынки Ташкента — находите магазины и получайте скидки
        </p>
      </div>

      {/* Map placeholder — Leaflet integration point */}
      <div className="bazaar-map-container">
        <div className="bazaar-map-placeholder">
          <div className="map-overlay">
            <span className="map-emoji">🗺️</span>
            <p>Интерактивная карта</p>
            <p className="map-hint">Подключите Leaflet для отображения карты с маркерами базаров</p>
          </div>
        </div>
      </div>

      {/* Bazaar List */}
      <section className="section container">
        <h2 className="section-title" style={{ marginBottom: 'var(--space-lg)' }}>
          Все объекты ({bazaars.length})
        </h2>
        <div className="bazaar-list">
          {bazaars.map((bazaar) => (
            <Link key={bazaar.id} to={`/bazaar/${bazaar.id}`} className="bazaar-card">
              <div className="bazaar-card__header">
                <span
                  className="bazaar-card__type"
                  style={{ background: typeColors[bazaar.type] + '20', color: typeColors[bazaar.type] }}
                >
                  {typeLabels[bazaar.type] || bazaar.type}
                </span>
              </div>
              <h3 className="bazaar-card__name">{bazaar.name}</h3>
              {bazaar.description && (
                <p className="bazaar-card__desc">{bazaar.description}</p>
              )}
              <div className="bazaar-card__info">
                {bazaar.address && (
                  <span className="bazaar-card__detail">
                    <MapPin size={14} /> {bazaar.address}
                  </span>
                )}
                {bazaar.workingHours && (
                  <span className="bazaar-card__detail">
                    <Clock size={14} /> {bazaar.workingHours}
                  </span>
                )}
                {bazaar.phone && (
                  <span className="bazaar-card__detail">
                    <Phone size={14} /> {bazaar.phone}
                  </span>
                )}
              </div>
              <div className="bazaar-card__footer">
                <span className="bazaar-card__link">
                  Открыть карту <ArrowLeft size={14} style={{ transform: 'rotate(180deg)' }} />
                </span>
              </div>
            </Link>
          ))}
        </div>
      </section>
    </div>
  );
}
