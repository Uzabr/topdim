import { useMemo, useCallback } from 'react';
import { Search, Crosshair, MapPin, Store } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { directoryApi } from '../api/bazaars';
import type { Bazaar, Shop } from '../api/bazaars';
import TwoGisMap from '../components/map/TwoGisMap';
import DirectoryBazaarCard from '../components/directory/DirectoryBazaarCard';
import DirectoryShopCard from '../components/directory/DirectoryShopCard';
import ResultsPanel from '../components/directory/ResultsPanel';
import { useDirectoryStore } from '../store/directoryStore';
import { bazaarSpots } from '../data/topdim';
import './BazaarMapPage.css';

/** Map BazaarSpot demo data to Bazaar type for fallback */
const DEMO_BAZAARS: Bazaar[] = bazaarSpots.map((s) => ({
  id: s.id,
  name: s.name,
  nameUz: undefined,
  type: s.type,
  address: s.address,
  city: s.city,
  latitude: s.latitude ?? 41.3111,
  longitude: s.longitude ?? 69.2797,
  description: s.description,
  coverImageUrl: s.image,
  workingHours: s.workingHours,
  phone: s.phone,
  status: 'ACTIVE',
  shopCount: s.itemsCount,
}));

const DEMO_SHOPS: Shop[] = [
  { id: 1, name: 'Специи от Мехмона', category: 'Специи', goodsDescription: 'Зира, куркума, паприка, шафран', locationType: 'BAZAAR', bazaar: { id: 1, name: 'Chorsu Select' }, rowNumber: '3', shopNumber: '25', latitude: 41.3265, longitude: 69.2289, photos: [], status: 'ACTIVE' },
  { id: 2, name: 'Korzinka Go', category: 'Продукты', goodsDescription: 'Продукты, товары для дома', locationType: 'STANDALONE', address: 'ул. Амира Темура, 48', latitude: 41.3111, longitude: 69.2797, photos: [], status: 'ACTIVE' },
  { id: 3, name: 'Samsung Brand Store', category: 'Электроника', goodsDescription: 'Смартфоны, ТВ, бытовая техника', locationType: 'BAZAAR', bazaar: { id: 2, name: 'Eco Mall Bazaar' }, shopNumber: '118', latitude: 41.3112, longitude: 69.2792, photos: [], status: 'ACTIVE' },
];

const TYPE_OPTIONS = ['Все', 'BAZAAR_MARKET', 'SHOPPING_CENTER', 'TRADE_COMPLEX'];
const TYPE_LABELS: Record<string, string> = {
  Все: 'Все',
  BAZAAR_MARKET: 'Базар / Рынок',
  SHOPPING_CENTER: 'ТЦ',
  TRADE_COMPLEX: 'Т. Комплекс',
};

/** BAZAAR_MARKET filter matches both BAZAAR and MARKET backend types */
const TYPE_FILTER_MAP: Record<string, string[]> = {
  BAZAAR_MARKET: ['BAZAAR', 'MARKET'],
  SHOPPING_CENTER: ['SHOPPING_CENTER'],
  TRADE_COMPLEX: ['TRADE_COMPLEX'],
};

export default function BazaarMapPage() {
  const {
    search, activeTab, bazaarTypeFilter, isAreaSelecting,
    areaBazaars, areaShops, showResultsPanel, selectedBazaarId,
    setSearch, setActiveTab, setBazaarTypeFilter,
    toggleAreaSelecting, setAreaResults, clearAreaResults, selectBazaar,
  } = useDirectoryStore();

  // Fetch bazaars from API (fallback to demo)
  const { data: bazaarsData } = useQuery({
    queryKey: ['directory-bazaars', search],
    queryFn: () => directoryApi.getBazaars({
      search: search || undefined,
    }),
    select: (res) => res.data.data,
  });

  // Fetch standalone shops (only when showing "Все" or for search)
  const { data: standaloneShopsData } = useQuery({
    queryKey: ['directory-shops-standalone', search],
    queryFn: () => directoryApi.getShops({
      search: search || undefined,
      size: 50,
    }),
    select: (res) => res.data.data,
  });

  const allBazaars = bazaarsData ?? DEMO_BAZAARS;
  const allShops = standaloneShopsData ?? DEMO_SHOPS;

  // Filter standalone shops (not inside bazaars)
  const standaloneShops = useMemo(() =>
    allShops.filter((s) => s.locationType === 'STANDALONE'),
  [allShops]);

  // Filter bazaars by type filter + search
  const filteredBazaars = useMemo(() => {
    let result = allBazaars;

    // Type filter
    if (bazaarTypeFilter !== 'Все') {
      const allowedTypes = TYPE_FILTER_MAP[bazaarTypeFilter] || [];
      result = result.filter((b) => allowedTypes.includes(b.type));
    }

    // Search filter (client-side supplement)
    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter((b) =>
        b.name.toLowerCase().includes(q) ||
        b.address?.toLowerCase().includes(q) ||
        b.description?.toLowerCase().includes(q)
      );
    }

    return result;
  }, [allBazaars, search, bazaarTypeFilter]);

  // Filter standalone shops by search
  const filteredShops = useMemo(() => {
    if (!search.trim()) return standaloneShops;
    const q = search.toLowerCase();
    return standaloneShops.filter((s) =>
      s.name.toLowerCase().includes(q) ||
      s.category?.toLowerCase().includes(q) ||
      s.goodsDescription?.toLowerCase().includes(q)
    );
  }, [standaloneShops, search]);

  // Show standalone shops only when "Все" is selected
  const showStandaloneSection = bazaarTypeFilter === 'Все';

  // Handle area select from map
  const handleAreaSelect = useCallback(async (bounds: { minLat: number; maxLat: number; minLon: number; maxLon: number }) => {
    let localBazaars: Bazaar[] = [];
    let localShops: Shop[] = [];

    try {
      const res = await directoryApi.searchInArea(bounds);
      localBazaars = res.data.data.bazaars;
      localShops = res.data.data.shops;
    } catch {
      // Fallback: filter demo data by bounds
      localBazaars = allBazaars.filter((b) =>
        b.latitude >= bounds.minLat && b.latitude <= bounds.maxLat &&
        b.longitude >= bounds.minLon && b.longitude <= bounds.maxLon
      );
      localShops = DEMO_SHOPS.filter((s) =>
        s.latitude >= bounds.minLat && s.latitude <= bounds.maxLat &&
        s.longitude >= bounds.minLon && s.longitude <= bounds.maxLon
      );
    }

    interface TwoGisCatalogItem {
      name: string;
      type: string;
      rubrics?: { name: string }[];
      address_name?: string;
      address_comment?: string;
      point?: { lat: number; lon: number };
      schedule?: { name: string };
    }

    // 2GIS Catalog API Fetch
    let externalShops: Shop[] = [];
    try {
      const point1 = `${bounds.minLon},${bounds.maxLat}`; // Top-left
      const point2 = `${bounds.maxLon},${bounds.minLat}`; // Bottom-right
      const q = encodeURIComponent('магазин,базар,тц,рынок');
      const apikey = '3b3d04f2-7dc0-46bc-899b-2c8652fd4813';
      const url = `https://catalog.api.2gis.com/3.0/items?q=${q}&point1=${point1}&point2=${point2}&key=${apikey}&fields=items.point&page_size=10`;
      
      const resp = await fetch(url);
      const json = await resp.json();
      
      if (json.result && json.result.items) {
        externalShops = json.result.items.map((it: TwoGisCatalogItem, idx: number) => ({
          id: -(Date.now() + idx), // Fake negative ID for external
          name: it.name,
          type: 'STANDALONE',
          locationType: 'STANDALONE',
          category: it.rubrics?.[0]?.name || (it.type === 'branch' ? 'Магазин' : 'Базар'),
          address: it.address_name || it.address_comment || '',
          latitude: it.point?.lat || 0,
          longitude: it.point?.lon || 0,
          workingHours: it.schedule?.name || 'Внешний источник 2ГИС',
          photos: [],
          bazaarId: -1,
          isExternal: true,
          status: 'ACTIVE'
        } as Shop));
      }
    } catch (err) {
      console.error('Failed to fetch from 2GIS', err);
    }

    setAreaResults(localBazaars, [...localShops, ...externalShops], bounds);
  }, [allBazaars, setAreaResults]);

  return (
    <div className="bazaar-page">
      <section className="container bazaar-shell">
        {/* Title */}
        <div className="dir-header">
          <div>
            <h1 className="page-title">Справочник базаров и магазинов</h1>
            <p className="section-copy">
              Ищите базары и магазины на карте, выделяйте область для поиска, или выберите из списка.
            </p>
          </div>
        </div>

        {/* Search + Filters */}
        <div className="dir-tools">
          <div className="dir-search">
            <Search size={20} />
            <input
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Поиск по базарам и магазинам…"
              className="input-field"
              aria-label="Поиск"
            />
          </div>

          <div className="dir-filters">
            {TYPE_OPTIONS.map((t) => (
              <button
                key={t}
                className={`filter-chip ${bazaarTypeFilter === t ? 'filter-chip--active' : ''}`}
                onClick={() => setBazaarTypeFilter(t)}
              >
                {TYPE_LABELS[t]}
              </button>
            ))}
          </div>
        </div>

        {/* Main layout: sidebar + map */}
        <div className="dir-layout">
          {/* Sidebar — bazaar + shop list */}
          <aside className="dir-sidebar">
            <h2 className="dir-sidebar__title">
              <MapPin size={18} />
              Базары ({filteredBazaars.length})
            </h2>
            <div className="dir-sidebar__list">
              {filteredBazaars.map((b) => (
                <DirectoryBazaarCard key={b.id} bazaar={b} compact />
              ))}
              {filteredBazaars.length === 0 && (
                <p className="dir-sidebar__empty">Базары не найдены</p>
              )}
            </div>

            {/* Standalone shops section — only visible when "Все" */}
            {showStandaloneSection && filteredShops.length > 0 && (
              <>
                <h2 className="dir-sidebar__title dir-sidebar__title--shops">
                  <Store size={18} />
                  Магазины ({filteredShops.length})
                </h2>
                <div className="dir-sidebar__list">
                  {filteredShops.map((s) => (
                    <DirectoryShopCard key={s.id} shop={s} compact />
                  ))}
                </div>
              </>
            )}
          </aside>

          {/* Map */}
          <div className="dir-map-area">
            <div className="dir-map-toolbar">
              <button
                className={`dir-draw-btn ${isAreaSelecting ? 'dir-draw-btn--active' : ''}`}
                onClick={toggleAreaSelecting}
              >
                <Crosshair size={18} />
                {isAreaSelecting ? 'Отменить выделение' : 'Выделить область'}
              </button>
              {isAreaSelecting && (
                <span className="dir-draw-hint">
                  Зажмите кнопку мыши и обведите нужную область на карте.
                </span>
              )}
            </div>

            <TwoGisMap
              bazaars={filteredBazaars}
              selectedBazaarId={selectedBazaarId}
              onBazaarClick={(id) => selectBazaar(id)}
              isDrawing={isAreaSelecting}
              onAreaSelect={handleAreaSelect}
            />
          </div>
        </div>

        {/* Results panel (after area selection) */}
        {showResultsPanel && (
          <ResultsPanel
            bazaars={areaBazaars}
            shops={areaShops}
            activeTab={activeTab}
            onTabChange={setActiveTab}
            onClose={clearAreaResults}
          />
        )}
      </section>
    </div>
  );
}
