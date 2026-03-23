import { useParams, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, MapPin, Clock, Phone, Tag, Store, ExternalLink } from 'lucide-react';
import { bazaarsApi } from '../api/bazaars';
import type { Shop } from '../api/bazaars';
import './ShopDetailPage.css';

const DEMO_SHOP: Shop = {
  id: 1, bazaar: { id: 1, name: 'Чорсу базар' },
  name: 'Специи от Мехмона', rowNumber: '3', shopNumber: '25',
  category: { id: 1, name: 'Специи' },
  goodsDescription: 'Зира, куркума, паприка, шафран, барбарис, лавровый лист и более 50 видов специй. Всё свежее, привозим напрямую из Ферганской долины.',
  workingHours: '08:00 – 17:00', phone: '+998 90 111 22 33',
  hasCoupon: true, linkedCouponOfferId: 1, floorNumber: 1, zoneId: 'A',
  productTags: [{ tag: 'Специи' }, { tag: 'Сухофрукты' }, { tag: 'Орехи' }],
};

export default function ShopDetailPage() {
  const { id } = useParams<{ id: string }>();

  const { data: shop } = useQuery({
    queryKey: ['shop', id],
    queryFn: () => bazaarsApi.getShop(Number(id)),
    select: (res) => res.data.data,
    enabled: !!id,
  });

  const s = shop || DEMO_SHOP;

  return (
    <div className="shop-detail">
      <div className="shop-detail__topbar container">
        <Link to={`/bazaar/${s.bazaar.id}`} className="detail-back">
          <ArrowLeft size={20} /> {s.bazaar.name}
        </Link>
      </div>

      <div className="shop-detail__content container">
        <div className="shop-detail__hero-card glass">
          <div className="shop-detail__photo">
            {s.photoUrl ? (
              <img src={s.photoUrl} alt={s.name} />
            ) : (
              <div className="shop-detail__placeholder">🏬</div>
            )}
          </div>

          <div className="shop-detail__info">
            <div className="shop-detail__badges">
              {s.category && <span className="shop-detail__cat">{s.category.name}</span>}
              {s.hasCoupon && <span className="shop-detail__coupon-badge">🎫 Есть купон</span>}
            </div>

            <h1>{s.name}</h1>

            <div className="shop-detail__location">
              <Store size={16} /> {s.bazaar.name}
              {s.rowNumber && <> · Ряд {s.rowNumber}</>}
              {s.shopNumber && <> · Место {s.shopNumber}</>}
              {s.floorNumber > 0 && <> · Этаж {s.floorNumber}</>}
              {s.zoneId && <> · Зона {s.zoneId}</>}
            </div>
          </div>
        </div>

        <div className="shop-detail__body">
          <div className="shop-detail__main">
            {s.goodsDescription && (
              <div className="shop-detail__block">
                <h2>Ассортимент</h2>
                <p>{s.goodsDescription}</p>
              </div>
            )}

            {s.productTags.length > 0 && (
              <div className="shop-detail__block">
                <h2>Теги товаров</h2>
                <div className="shop-detail__tags">
                  {s.productTags.map((t, i) => (
                    <span key={i} className="shop-tag-lg"><Tag size={14} /> {t.tag}</span>
                  ))}
                </div>
              </div>
            )}

            {s.hasCoupon && s.linkedCouponOfferId && (
              <div className="shop-detail__coupon-link">
                <h2>💰 Купон со скидкой</h2>
                <Link to={`/coupons/${s.linkedCouponOfferId}`} className="shop-detail__coupon-btn">
                  Посмотреть купон <ExternalLink size={16} />
                </Link>
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
            <div className="shop-detail__contact">
              <MapPin size={16} /> {s.bazaar.name}
              {s.rowNumber && `, ряд ${s.rowNumber}`}
              {s.shopNumber && `, место ${s.shopNumber}`}
            </div>
          </aside>
        </div>
      </div>
    </div>
  );
}
