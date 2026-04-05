import { useMemo } from 'react';
import { Compass, LocateFixed, MapPinned, Route, Sparkles, Star } from 'lucide-react';
import { YMaps, Map, Clusterer, Placemark } from '@pbe/react-yandex-maps';
import SearchBar from '../components/ui/SearchBar';
import FilterBar from '../components/marketplace/FilterBar';
import BazaarCard from '../components/marketplace/BazaarCard';
import { bazaarItems, bazaarSpots } from '../data/topdim';
import { useMarketplaceStore } from '../store/marketplaceStore';
import './BazaarMapPage.css';

export default function BazaarMapPage() {
  const {
    search,
    category,
    priceRange,
    distance,
    viewMode,
    nearMeOnly,
    selectedBazaarId,
    setSearch,
    setCategory,
    setPriceRange,
    setDistance,
    toggleNearMe,
    selectBazaar,
  } = useMarketplaceStore();

  const visibleItems = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();

    return bazaarItems.filter((item) => {
      const matchesSearch =
        normalizedSearch.length === 0 ||
        `${item.title} ${item.bazaarName} ${item.category}`.toLowerCase().includes(normalizedSearch);
      const matchesCategory = category === 'Все' || item.category === category;
      const matchesDistance =
        distance === 'До 1 км' ? item.distanceKm <= 1 :
        distance === 'До 5 км' ? item.distanceKm <= 5 :
        item.distanceKm <= 10;
      const matchesPrice =
        priceRange === 'Любая цена' ||
        (priceRange === 'До 100k' && item.price <= 100000) ||
        (priceRange === '100k–250k' && item.price > 100000 && item.price <= 250000) ||
        (priceRange === '250k+' && item.price > 250000);
      const matchesNearMe = !nearMeOnly || item.distanceKm <= 3.5;

      return matchesSearch && matchesCategory && matchesDistance && matchesPrice && matchesNearMe;
    });
  }, [category, distance, nearMeOnly, priceRange, search]);

  const selectedSpot =
    bazaarSpots.find((spot) => spot.id === selectedBazaarId) ?? bazaarSpots[0];

  return (
    <div className="bazaar-page">
      <section className="container bazaar-shell">
        <div className="bazaar-intro surface-card">
          <div>
            <div className="pill bazaar-intro__pill">
              <Sparkles size={16} />
              Online bazaar discovery
            </div>
            <h1 className="page-title">Карта скидок и живой базар в одном экране</h1>
            <p className="section-copy">
              Ищи товары по расстоянию, смотри пины с дисконтом, переключайся между grid и list,
              и забирай лучшие находки рядом с собой.
            </p>
          </div>

          <div className="bazaar-intro__stats">
            <div>
              <strong>86</strong>
              <span>активных drop-точек</span>
            </div>
            <div>
              <strong>4.8</strong>
              <span>средний рейтинг базара</span>
            </div>
            <div>
              <strong>15 мин</strong>
              <span>до новой волны скидок</span>
            </div>
          </div>
        </div>

        <div className="bazaar-tools">
          <SearchBar
            value={search}
            onChange={setSearch}
            placeholder="Что хочешь найти на базаре?"
          />

          <FilterBar
            category={category}
            priceRange={priceRange}
            distance={distance}
            onCategoryChange={setCategory}
            onPriceRangeChange={setPriceRange}
            onDistanceChange={setDistance}
          />

          <div className="bazaar-toolbar surface-card">
            <button type="button" className={`toolbar-pill ${nearMeOnly ? 'toolbar-pill--active' : ''}`} onClick={toggleNearMe}>
              <LocateFixed size={16} />
              Показать рядом со мной
            </button>
          </div>
        </div>

        <div className="bazaar-layout">
          <div className="bazaar-map surface-card">
            <div className="bazaar-map__topline">
              <div>
                <p className="section-label">Map</p>
                <h2 className="section-title">Смотри, где сейчас самые вкусные скидки</h2>
              </div>
            </div>

            <div className="bazaar-map__frame">
              <YMaps query={{ lang: 'ru_RU', apikey: 'REMOVED_MAP_API_KEY' }}>
                <Map 
                  defaultState={{ center: [41.3111, 69.2797], zoom: 12 }} 
                  className="topdim-map"
                >
                  <Clusterer
                    options={{
                      preset: 'islands#invertedNightClusterIcons',
                      groupByCoordinates: false,
                      clusterDisableClickZoom: false,
                      minClusterSize: 2, // убрать метки 1 в кластере
                    }}
                  >
                    {bazaarSpots.map((spot) => (
                      <Placemark
                        key={spot.id}
                        geometry={[spot.latitude ?? 41.3111, spot.longitude ?? 69.2797]}
                        properties={{
                          iconContent: spot.discountLabel,
                          balloonContentHeader: spot.name,
                          balloonContentBody: spot.spotlight,
                        }}
                        options={{
                          preset: spot.id === selectedSpot.id ? 'islands#redStretchyIcon' : 'islands#blackStretchyIcon',
                        }}
                        onClick={() => selectBazaar(spot.id)}
                      />
                    ))}
                  </Clusterer>
                </Map>
              </YMaps>

              <article className="map-preview surface-card">
                <img src={selectedSpot.image} alt={selectedSpot.name} />
                <div className="map-preview__content">
                  <div className="map-preview__badge">
                    <MapPinned size={14} />
                    {selectedSpot.discountLabel}
                  </div>
                  <h3>{selectedSpot.name}</h3>
                  <p>{selectedSpot.description}</p>
                  <div className="map-preview__meta">
                    <span>
                      <Star size={14} fill="currentColor" />
                      {selectedSpot.rating}
                    </span>
                    <span>
                      <Route size={14} />
                      {selectedSpot.distanceKm} км
                    </span>
                    <span>
                      <Compass size={14} />
                      {selectedSpot.itemsCount} товаров
                    </span>
                  </div>
                </div>
              </article>
            </div>
          </div>

          <aside className="bazaar-sidebar">
            {bazaarSpots.map((spot) => (
              <button
                key={spot.id}
                type="button"
                className={`spot-card surface-card ${spot.id === selectedSpot.id ? 'spot-card--active' : ''}`}
                onClick={() => selectBazaar(spot.id)}
              >
                <img src={spot.image} alt={spot.name} />
                <div>
                  <span>{spot.discountLabel}</span>
                  <strong>{spot.name}</strong>
                  <p>{spot.spotlight}</p>
                </div>
              </button>
            ))}
          </aside>
        </div>

        <section className="bazaar-products">
          <div className="section-heading">
            <div>
              <p className="section-label">Products / bazaar items</p>
              <h2 className="section-title">Находки, которые хочется открыть прямо сейчас</h2>
            </div>
            <div className="bazaar-products__meta">{visibleItems.length} товаров найдено</div>
          </div>

          <div className={`bazaar-products__grid bazaar-products__grid--${viewMode}`}>
            {visibleItems.map((item) => (
              <BazaarCard key={item.id} item={item} viewMode={viewMode} />
            ))}
          </div>
        </section>
      </section>
    </div>
  );
}
