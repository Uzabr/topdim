import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, MapPin, Clock, Phone, Tag, Store, Image } from 'lucide-react';
import { directoryApi } from '../api/bazaars';
import type { Shop } from '../api/bazaars';
import TwoGisMap from '../components/map/TwoGisMap';
import { useLocalePath } from '../hooks/useLocalePath';
import './ShopDetailPage.css';

const DEMO_SHOP: Shop = {
  id: 1, name: 'Специи от Мехмона',
  description: 'Лучшие специи Ферганской долины. Привозим напрямую от фермеров.',
  category: 'Специи', goodsDescription: 'Зира, куркума, паприка, шафран, барбарис, лавровый лист и более 50 видов специй.',
  workingHours: '08:00 – 17:00', phone: '+998 90 111 22 33',
  locationType: 'BAZAAR',
  bazaar: { id: 1, name: 'Чорсу базар' },
  rowNumber: '3', shopNumber: '25', floorNumber: 1,
  latitude: 41.3265, longitude: 69.2289,
  photos: [], status: 'ACTIVE',
};

export default function ShopDetailPage() {
  const { id } = useParams<{ id: string }>();
  const lp = useLocalePath();

  const { data: shop } = useQuery({
    queryKey: ['shop', id],
    queryFn: () => directoryApi.getShopById(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const s = shop || DEMO_SHOP;
  const isBazaar = s.locationType === 'BAZAAR';

  return (
    <div className="shop-detail">
      <div className="shop-detail__topbar container">
        {isBazaar && s.bazaar ? (
          <Link to={lp(`/bazaar/${s.bazaar.id}`)} className="detail-back">
            <ArrowLeft size={20} /> {s.bazaar.name}
          </Link>
        ) : (
          <Link to={lp('/bazaar')} className="detail-back">
            <ArrowLeft size={20} /> Справочник
          </Link>
        )}
      </div>

      <div className="shop-detail__content container">
        {/* Hero card */}
        <div className="shop-detail__hero-card glass">
          <div className="shop-detail__photo">
            {s.photos?.[0] ? (
              <img src={s.photos[0]} alt={s.name} />
            ) : (
              <div className="shop-detail__placeholder">🏬</div>
            )}
          </div>

          <div className="shop-detail__info">
            <div className="shop-detail__badges">
              {s.category && <span className="shop-detail__cat">{s.category}</span>}
              <span className={`shop-detail__location-badge ${isBazaar ? '' : 'shop-detail__location-badge--standalone'}`}>
                {isBazaar ? '📍 В базаре' : '🏠 Отдельный'}
              </span>
            </div>

            <h1>{s.name}</h1>

            <div className="shop-detail__location">
              {isBazaar && s.bazaar ? (
                <>
                  <Store size={16} /> {s.bazaar.name}
                  {s.pavilion && <> · {s.pavilion}</>}
                  {s.sector && <> · Сектор {s.sector}</>}
                  {s.rowNumber && <> · Ряд {s.rowNumber}</>}
                  {s.shopNumber && <> · Место {s.shopNumber}</>}
                  {s.floorNumber && s.floorNumber > 0 && <> · Этаж {s.floorNumber}</>}
                </>
              ) : (
                <>
                  <MapPin size={16} /> {s.address || 'Адрес не указан'}
                </>
              )}
            </div>
          </div>
        </div>

        <div className="shop-detail__body">
          <div className="shop-detail__main">
            {/* Description */}
            {s.description && (
              <div className="shop-detail__block">
                <h2>О магазине</h2>
                <p>{s.description}</p>
              </div>
            )}

            {/* Goods */}
            {s.goodsDescription && (
              <div className="shop-detail__block">
                <h2>Ассортимент</h2>
                <p>{s.goodsDescription}</p>
              </div>
            )}

            {/* Photo gallery */}
            {s.photos && s.photos.length > 0 && (
              <div className="shop-detail__block">
                <h2><Image size={18} /> Фото</h2>
                <div className="shop-detail__gallery">
                  {s.photos.map((url, i) => (
                    <img key={i} src={url} alt={`${s.name} фото ${i + 1}`} className="shop-detail__gallery-img" />
                  ))}
                </div>
              </div>
            )}

            {/* Map for standalone shops */}
            {!isBazaar && s.latitude && s.longitude && (
              <div className="shop-detail__block">
                <h2><MapPin size={18} /> На карте</h2>
                <TwoGisMap
                  staticMarker={{ lat: s.latitude, lon: s.longitude, title: s.name }}
                  className="twogis-map--detail"
                />
              </div>
            )}
          </div>

          <aside className="shop-detail__sidebar glass">
            <h2>Контакты</h2>
            {s.workingHours && (
              <div className="shop-detail__contact">
                <Clock size={16} /> {s.workingHours}
              </div>
            )}
            {s.phone && (
              <div className="shop-detail__contact">
                <Phone size={16} />
                <a href={`tel:${s.phone}`}>{s.phone}</a>
              </div>
            )}
            {s.category && (
              <div className="shop-detail__contact">
                <Tag size={16} /> {s.category}
                {s.subcategory && ` · ${s.subcategory}`}
              </div>
            )}
            <div className="shop-detail__contact">
              {isBazaar && s.bazaar ? (
                <>
                  <Store size={16} /> {s.bazaar.name}
                  {s.rowNumber && `, ряд ${s.rowNumber}`}
                  {s.shopNumber && `, место ${s.shopNumber}`}
                </>
              ) : (
                <>
                  <MapPin size={16} /> {s.address}
                </>
              )}
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
