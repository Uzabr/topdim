import { Heart, ArrowRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useFavoritesStore } from '../store/favoritesStore';
import CouponCard from '../components/coupon/CouponCard';
import type { CouponCardData } from '../components/coupon/CouponCard';
import { topdimDeals } from '../data/topdim';
import './FavoritesPage.css';

export default function FavoritesPage() {
  const { favoriteIds } = useFavoritesStore();

  // Map favorite IDs to actual coupon data
  // TODO: Replace with API call when backend is fully integrated
  const mapped: CouponCardData[] = favoriteIds
    .map((id) => {
      const deal = topdimDeals.find((d) => d.id === id);
      if (!deal) return null;
      return {
        id: deal.id,
        title: deal.title,
        shortDescription: deal.shortDescription,
        merchant: deal.merchant || { id: 0, name: 'TopDim' },
        category: deal.category,
        oldPrice: deal.oldPrice,
        fromPrice: deal.fromPrice,
        discountPercent: deal.discountPercent,
        coverImageUrl: deal.coverImageUrl || deal.image,
        totalSold: deal.totalSold,
        rating: deal.rating,
        reviewCount: deal.reviews,
        location: deal.location,
        isHot: deal.isHot,
        giftAvailable: deal.giftAvailable,
      } as CouponCardData;
    })
    .filter(Boolean) as CouponCardData[];

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
          <Link to="/coupons" className="primary-button">
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
