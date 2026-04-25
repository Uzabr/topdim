import { Heart, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { useFavoritesStore } from '../store/favoritesStore';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { couponsApi } from '../api/coupons';
import { useLocalePath } from '../hooks/useLocalePath';
import { mapCouponOfferToCardData } from '../utils/couponCardMapper';
import './FavoritesPage.css';

export default function FavoritesPage() {
  const { favoriteIds } = useFavoritesStore();
  const lp = useLocalePath();

  const { data: couponsData } = useQuery({
    queryKey: ['coupons-favorites'],
    queryFn: () => couponsApi.getCatalog({ size: 200 }),
    select: (res) => res.data.data.content,
  });

  const apiDeals = couponsData || [];

  const mappedDeals: CouponCardData[] = apiDeals.map(mapCouponOfferToCardData);

  const mapped = mappedDeals.filter(deal => favoriteIds.includes(deal.id));

  return (
    <div className="favorites-page container">
      <div className="favorites-header">
        <Heart size={24} className="favorites-icon" />
        <h1>Избранное</h1>
        {mapped.length > 0 && (
          <span className="favorites-count">{mapped.length}</span>
        )}
      </div>

      {mapped.length === 0 ? (
        <div className="favorites-empty surface-card">
          <span className="favorites-empty__icon">💛</span>
          <h3>Пока пусто</h3>
          <p>Добавляйте понравившиеся купоны, нажимая на ❤️ на карточке</p>
          <Link to={lp('/coupons')} className="primary-button">
            Перейти в каталог
            <ArrowRight size={16} />
          </Link>
        </div>
      ) : (
        <div className="favorites-grid">
          {mapped.map((deal) => (
            <CouponCard key={deal.id} coupon={deal} layout="card" />
          ))}
        </div>
      )}
    </div>
  );
}
