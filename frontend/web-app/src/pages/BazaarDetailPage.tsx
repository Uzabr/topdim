import { useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, MapPin, Clock, Phone, Store, Search } from 'lucide-react';
import { directoryApi } from '../api/bazaars';
import type { Bazaar, Shop } from '../api/bazaars';
import TwoGisMap from '../components/map/TwoGisMap';
import DirectoryShopCard from '../components/directory/DirectoryShopCard';
import './BazaarDetailPage.css';

const DEMO_BAZAAR: Bazaar = {
  id: 1, name: 'Чорсу базар', nameUz: 'Chorsu bozori',
  type: 'BAZAAR', address: 'ул. Навои, Старый город', city: 'Ташкент',
  latitude: 41.326, longitude: 69.232,
  description: 'Один из крупнейших базаров Центральной Азии. Специализируется на свежих продуктах, специях, сухофруктах и текстиле.',
  workingHours: '06:00 – 18:00', phone: '+998 71 244 00 00',
  status: 'ACTIVE', shopCount: 3,
};

const DEMO_SHOPS: Shop[] = [
  { id: 1, name: 'Специи от Мехмона', category: 'Специи', goodsDescription: 'Зира, куркума, паприка, шафран', locationType: 'BAZAAR', bazaar: { id: 1, name: 'Чорсу' }, rowNumber: '3', shopNumber: '25', floorNumber: 1, latitude: 41.3265, longitude: 69.2289, photos: [], status: 'ACTIVE' },
  { id: 2, name: 'Ткани Шёлковый путь', category: 'Текстиль', goodsDescription: 'Атлас, адрас, хан-атлас', locationType: 'BAZAAR', bazaar: { id: 1, name: 'Чорсу' }, rowNumber: '5', shopNumber: '10', floorNumber: 2, latitude: 41.3265, longitude: 69.2289, photos: [], status: 'ACTIVE' },
  { id: 3, name: 'Фрукты Ферганы', category: 'Фрукты', goodsDescription: 'Гранат, хурма, виноград', locationType: 'BAZAAR', bazaar: { id: 1, name: 'Чорсу' }, rowNumber: '1', shopNumber: '5', floorNumber: 1, latitude: 41.3265, longitude: 69.2289, photos: [], status: 'ACTIVE' },
];

const TYPE_LABELS: Record<string, string> = {
  BAZAAR: 'Базар', SHOPPING_CENTER: 'ТЦ', MARKET: 'Рынок', TRADE_COMPLEX: 'Торговый комплекс',
};

export default function BazaarDetailPage() {
  const { id } = useParams<{ id: string }>();
  const [shopSearch, setShopSearch] = useState('');

  const { data: bazaar } = useQuery({
    queryKey: ['bazaar', id],
    queryFn: () => directoryApi.getBazaarById(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const { data: shops } = useQuery({
    queryKey: ['bazaar-shops', id, shopSearch],
    queryFn: () => directoryApi.getShopsByBazaar(Number(id), shopSearch || undefined),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const b = bazaar || DEMO_BAZAAR;
  const shopList = shops || DEMO_SHOPS;

  const filteredShops = shopSearch.trim()
    ? shopList.filter((s) =>
        s.name.toLowerCase().includes(shopSearch.toLowerCase()) ||
        s.category?.toLowerCase().includes(shopSearch.toLowerCase()) ||
        s.goodsDescription?.toLowerCase().includes(shopSearch.toLowerCase())
      )
    : shopList;

  return (
    <div className="bazaar-detail">
      <div className="bazaar-detail__topbar container">
        <Link to="/bazaar" className="detail-back">
          <ArrowLeft size={20} /> Справочник
        </Link>
      </div>

      {/* Hero */}
      <div className="bazaar-detail__hero">
        {b.coverImageUrl ? (
          <img src={b.coverImageUrl} alt={b.name} />
        ) : (
          <div className="bazaar-detail__placeholder">🏪</div>
        )}
      </div>

      <div className="bazaar-detail__content container">
        <div className="bazaar-detail__info">
          <span className="bazaar-detail__type">{TYPE_LABELS[b.type] || b.type}</span>
          <h1>{b.name}</h1>
          {b.nameUz && <p className="bazaar-detail__name-uz">{b.nameUz}</p>}
          {b.description && <p className="bazaar-detail__desc">{b.description}</p>}

          <div className="bazaar-detail__meta">
            {b.address && <div><MapPin size={16} /> {b.address}</div>}
            {b.workingHours && <div><Clock size={16} /> {b.workingHours}</div>}
            {b.phone && <div><Phone size={16} /> <a href={`tel:${b.phone}`}>{b.phone}</a></div>}
          </div>
        </div>

        {/* Map */}
        <div className="bazaar-detail__map">
          <TwoGisMap
            staticMarker={{ lat: b.latitude, lon: b.longitude, title: b.name }}
            className="twogis-map--detail"
          />
        </div>

        {/* Shops */}
        <div className="bazaar-detail__shops">
          <div className="bazaar-detail__shops-header">
            <h2><Store size={20} /> Магазины ({filteredShops.length})</h2>
            <div className="bazaar-detail__shop-search">
              <Search size={16} />
              <input
                value={shopSearch}
                onChange={(e) => setShopSearch(e.target.value)}
                placeholder="Поиск по магазинам…"
              />
            </div>
          </div>
          <div className="shops-grid">
            {filteredShops.map((shop) => (
              <DirectoryShopCard key={shop.id} shop={shop} />
            ))}
            {filteredShops.length === 0 && (
              <p className="bazaar-detail__shops-empty">Магазины не найдены</p>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}
