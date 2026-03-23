import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, MapPin, Clock, Phone, Store, Tag } from 'lucide-react';
import { bazaarsApi } from '../api/bazaars';
import type { Bazaar, Shop } from '../api/bazaars';
import './BazaarDetailPage.css';

const DEMO_BAZAAR: Bazaar = {
  id: 1, name: 'Чорсу базар', nameUz: 'Chorsu bozori',
  type: 'BAZAAR', address: 'Ташкент, Чорсу', city: 'Ташкент',
  latitude: 41.326, longitude: 69.232,
  description: 'Один из крупнейших базаров Центральной Азии. Специализируется на свежих продуктах, специях, сухофруктах и текстиле.',
  workingHours: '06:00 – 18:00', phone: '+998 71 244 00 00',
};

const DEMO_SHOPS: Shop[] = [
  { id: 1, bazaar: { id: 1, name: 'Чорсу' }, name: 'Специи от Мехмона', rowNumber: '3', shopNumber: '25', category: { id: 1, name: 'Специи' }, goodsDescription: 'Зира, куркума, паприка, шафран', workingHours: '08:00-17:00', phone: '+998 90 111 22 33', hasCoupon: true, linkedCouponOfferId: 1, floorNumber: 1, productTags: [{ tag: 'Специи' }, { tag: 'Сухофрукты' }] },
  { id: 2, bazaar: { id: 1, name: 'Чорсу' }, name: 'Ткани Шёлковый путь', rowNumber: '5', shopNumber: '10', category: { id: 2, name: 'Текстиль' }, goodsDescription: 'Атлас, адрас, хан-атлас', workingHours: '09:00-18:00', hasCoupon: false, floorNumber: 2, productTags: [{ tag: 'Ткани' }, { tag: 'Атлас' }] },
  { id: 3, bazaar: { id: 1, name: 'Чорсу' }, name: 'Фрукты Ферганы', rowNumber: '1', shopNumber: '5', category: { id: 3, name: 'Фрукты' }, goodsDescription: 'Гранат, хурма, виноград', workingHours: '07:00-16:00', hasCoupon: true, linkedCouponOfferId: 2, floorNumber: 1, productTags: [{ tag: 'Фрукты' }] },
];

export default function BazaarDetailPage() {
  const { id } = useParams<{ id: string }>();

  const { data: bazaar } = useQuery({
    queryKey: ['bazaar', id],
    queryFn: () => bazaarsApi.getById(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const { data: shops } = useQuery({
    queryKey: ['bazaar-shops', id],
    queryFn: () => bazaarsApi.getShops(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const b = bazaar || DEMO_BAZAAR;
  const shopList = shops || DEMO_SHOPS;

  return (
    <div className="bazaar-detail">
      <div className="bazaar-detail__topbar container">
        <Link to="/bazaar" className="detail-back">
          <ArrowLeft size={20} /> Назад
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
          <span className="bazaar-detail__type">{b.type}</span>
          <h1>{b.name}</h1>
          {b.nameUz && <p className="bazaar-detail__name-uz">{b.nameUz}</p>}
          {b.description && <p className="bazaar-detail__desc">{b.description}</p>}

          <div className="bazaar-detail__meta">
            {b.address && <div><MapPin size={16} /> {b.address}</div>}
            {b.workingHours && <div><Clock size={16} /> {b.workingHours}</div>}
            {b.phone && <div><Phone size={16} /> {b.phone}</div>}
          </div>
        </div>

        {/* Shops */}
        <div className="bazaar-detail__shops">
          <h2><Store size={20} /> Магазины ({shopList.length})</h2>
          <div className="shops-grid">
            {shopList.map((shop) => (
              <Link to={`/shops/${shop.id}`} key={shop.id} className="shop-card">
                <div className="shop-card__header">
                  <h3>{shop.name}</h3>
                  {shop.hasCoupon && <span className="shop-card__coupon">🎫 Купон</span>}
                </div>
                {shop.category && (
                  <span className="shop-card__category">{shop.category.name}</span>
                )}
                {shop.goodsDescription && (
                  <p className="shop-card__goods">{shop.goodsDescription}</p>
                )}
                <div className="shop-card__footer">
                  {shop.rowNumber && <span>Ряд {shop.rowNumber}</span>}
                  {shop.shopNumber && <span>Место {shop.shopNumber}</span>}
                  {shop.floorNumber > 0 && <span>Этаж {shop.floorNumber}</span>}
                </div>
                {shop.productTags.length > 0 && (
                  <div className="shop-card__tags">
                    {shop.productTags.map((t, i) => (
                      <span key={i} className="shop-tag"><Tag size={12} /> {t.tag}</span>
                    ))}
                  </div>
                )}
              </Link>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
